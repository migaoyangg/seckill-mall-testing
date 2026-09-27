from __future__ import annotations

import json
import os
import re
import subprocess
import sys
import threading
import time
from datetime import datetime
from pathlib import Path
from typing import Optional

from sqlalchemy import delete

from .config import ARTIFACT_ROOT, WORKSPACE_ROOT
from .broker import TaskBroker, build_broker
from .appium_manager import AppiumHandle, ensure_appium, stop_appium
from .db import Artifact, CaseResult, Environment, Project, SessionLocal, TestRun, TestSuite
from .devices import acquire_device, release_device
from .junit_parser import parse_junit
from .log_stream import run_log_stream


FINAL_STATUSES = {"PASSED", "FAILED", "CANCELLED", "TIMEOUT"}
MARKER_PATTERN = re.compile(r"^[a-zA-Z0-9_ ()&|!.-]*$")


class RunManager:
    def __init__(self, broker: Optional[TaskBroker] = None) -> None:
        self._broker = broker or build_broker()
        self._processes: dict[int, subprocess.Popen[str]] = {}
        self._appium_handles: dict[int, AppiumHandle] = {}
        self._cancelled: set[int] = set()
        self._lock = threading.Lock()
        self._thread: Optional[threading.Thread] = None

    def start(self) -> None:
        if self._thread and self._thread.is_alive():
            return
        self._thread = threading.Thread(target=self._worker_loop, name="testflow-worker", daemon=True)
        self._thread.start()

    def submit(self, run_id: int) -> None:
        self._broker.enqueue(run_id)

    def cancel(self, run_id: int) -> bool:
        with self._lock:
            self._cancelled.add(run_id)
            process = self._processes.get(run_id)
        if process and process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
        with SessionLocal() as db:
            run = db.get(TestRun, run_id)
            if not run or run.status in FINAL_STATUSES:
                return False
            run.status = "CANCELLED"
            run.finished_at = datetime.utcnow()
            run.error_message = "Task cancelled by user"
            db.commit()
        return True

    def recover_pending(self) -> None:
        with SessionLocal() as db:
            interrupted = db.query(TestRun).filter(TestRun.status == "RUNNING").all()
            for run in interrupted:
                run.status = "FAILED"
                run.finished_at = datetime.utcnow()
                run.error_message = "Platform restarted while task was running"
            pending = db.query(TestRun).filter(TestRun.status == "PENDING").order_by(TestRun.id).all()
            db.commit()
            for run in pending:
                self.submit(run.id)

    def _worker_loop(self) -> None:
        while True:
            run_id = self._broker.dequeue(timeout=2)
            if run_id is None:
                continue
            try:
                self._execute(run_id)
            except Exception as exc:  # keep the worker alive and persist the failure
                self._mark_internal_failure(run_id, exc)
            finally:
                with self._lock:
                    appium_handle = self._appium_handles.pop(run_id, None)
                stop_appium(appium_handle)
                release_device(run_id)

    def _execute(self, run_id: int) -> None:
        with SessionLocal() as db:
            run = db.get(TestRun, run_id)
            if not run or run.status != "PENDING" or run_id in self._cancelled:
                return
            suite = db.get(TestSuite, run.suite_id)
            environment = db.get(Environment, run.environment_id)
            project = db.get(Project, run.project_id)
            if not suite or not environment or not project:
                raise RuntimeError("Run references missing project, environment, or suite")
            run.status = "RUNNING"
            run.started_at = datetime.utcnow()
            db.commit()

            work_dir = self._safe_directory(project.work_dir)
            test_path = self._safe_test_path(work_dir, suite.test_path)
            if suite.marker and not MARKER_PATTERN.fullmatch(suite.marker):
                raise ValueError("Unsafe pytest marker expression")
            timeout = suite.timeout_seconds
            variables = json.loads(environment.variables_json or "{}")
            base_url = environment.base_url
            run_no = run.run_no
            device_serial = None
            if suite.device_required:
                device = acquire_device(db, run_id)
                if not device:
                    raise RuntimeError("No online and unlocked Android device is available")
                device_serial = device.serial

        run_dir = ARTIFACT_ROOT / run_no
        run_dir.mkdir(parents=True, exist_ok=True)
        junit_path = run_dir / "junit.xml"
        html_path = run_dir / "report.html"
        log_path = run_dir / "execution.log"
        appium_log_path = None
        if suite.test_type == "android":
            appium_handle = ensure_appium(run_dir)
            appium_log_path = appium_handle.log_path
            with self._lock:
                self._appium_handles[run_id] = appium_handle

        command = [
            self._python_executable_for(test_path),
            "-m",
            "pytest",
            str(test_path),
            "-q",
            f"--junitxml={junit_path}",
            f"--html={html_path}",
            "--self-contained-html",
        ]
        if suite.marker:
            command.extend(["-m", suite.marker])

        child_env = os.environ.copy()
        child_env.update({str(key): str(value) for key, value in variables.items()})
        if base_url:
            child_env["BASE_URL"] = base_url
        if device_serial:
            child_env["ANDROID_SERIAL"] = device_serial
            child_env["ANDROID_UDID"] = device_serial
            child_env["UDID"] = device_serial
        if suite.test_type == "android":
            child_env["APPIUM_SERVER_URL"] = appium_handle.url

        started = time.monotonic()
        with log_path.open("w", encoding="utf-8") as log_file:
            command_line = "COMMAND: " + " ".join(command) + "\n\n"
            log_file.write(command_line)
            log_file.flush()
            run_log_stream.publish(run_id, command_line)
            process = subprocess.Popen(
                command,
                cwd=work_dir,
                env=child_env,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                start_new_session=True,
                bufsize=1,
            )
            def pump_output() -> None:
                assert process.stdout is not None
                for output_line in process.stdout:
                    log_file.write(output_line)
                    log_file.flush()
                    run_log_stream.publish(run_id, output_line)

            output_thread = threading.Thread(target=pump_output, name=f"run-log-{run_id}", daemon=True)
            output_thread.start()
            with self._lock:
                self._processes[run_id] = process
            timed_out = False
            while process.poll() is None:
                if time.monotonic() - started >= timeout:
                    timed_out = True
                    process.terminate()
                    log_file.write(f"\nTask timed out after {timeout} seconds.\n")
                    break
                with SessionLocal() as status_db:
                    latest = status_db.get(TestRun, run_id)
                    cancelled = latest is not None and latest.status == "CANCELLED"
                if cancelled:
                    process.terminate()
                    break
                time.sleep(0.25)
            try:
                return_code = process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
                return_code = process.wait(timeout=5)
            finally:
                output_thread.join(timeout=2)
                with self._lock:
                    self._processes.pop(run_id, None)
        elapsed = round(time.monotonic() - started, 3)

        with SessionLocal() as db:
            run = db.get(TestRun, run_id)
            if not run:
                return
            db.execute(delete(CaseResult).where(CaseResult.run_id == run_id))
            db.execute(delete(Artifact).where(Artifact.run_id == run_id))
            self._add_artifact(db, run_id, "LOG", log_path)
            if html_path.exists():
                self._add_artifact(db, run_id, "HTML_REPORT", html_path)
            if junit_path.exists():
                self._add_artifact(db, run_id, "JUNIT_XML", junit_path)
                report = parse_junit(junit_path)
                run.total_count = report.total
                run.passed_count = report.passed
                run.failed_count = report.failed
                run.skipped_count = report.skipped
                for case in report.cases:
                    db.add(
                        CaseResult(
                            run_id=run_id,
                            case_name=case.name,
                            class_name=case.class_name,
                            status=case.status,
                            duration=case.duration,
                            error_message=case.error_message,
                        )
                    )
            if appium_log_path and appium_log_path.exists():
                self._add_artifact(db, run_id, "APPIUM_LOG", appium_log_path)
            run.duration = elapsed
            run.finished_at = datetime.utcnow()
            if run.status == "CANCELLED" or run_id in self._cancelled:
                run.status = "CANCELLED"
                run.error_message = "Task cancelled by user"
            elif timed_out:
                run.status = "TIMEOUT"
                run.error_message = f"Task exceeded timeout of {timeout} seconds"
            elif return_code == 0:
                run.status = "PASSED"
                run.error_message = ""
            else:
                run.status = "FAILED"
                run.error_message = f"pytest exited with code {return_code}"
            db.commit()

    @staticmethod
    def _python_executable_for(test_path: Path) -> str:
        """Prefer a suite-local virtual environment when the suite owns one."""
        suite_root = test_path if test_path.is_dir() else test_path.parent
        candidates = [
            suite_root / ".venv" / "bin" / "python",
            suite_root / "venv" / "bin" / "python",
        ]
        for candidate in candidates:
            if candidate.is_file() and os.access(candidate, os.X_OK):
                # Keep the venv path instead of resolving its Python symlink;
                # CPython uses that path to locate pyvenv.cfg and site-packages.
                return str(candidate.absolute())
        return sys.executable

    @staticmethod
    def _safe_directory(raw_path: str) -> Path:
        path = Path(raw_path).expanduser().resolve()
        if path != WORKSPACE_ROOT and WORKSPACE_ROOT not in path.parents:
            raise ValueError("Project work_dir must stay inside the workspace")
        if not path.is_dir():
            raise ValueError(f"Project work_dir does not exist: {path}")
        return path

    @staticmethod
    def _safe_test_path(work_dir: Path, raw_path: str) -> Path:
        candidate = (work_dir / raw_path).resolve() if not Path(raw_path).is_absolute() else Path(raw_path).resolve()
        if candidate != work_dir and work_dir not in candidate.parents:
            raise ValueError("Test path escapes project work_dir")
        if not candidate.exists():
            raise ValueError(f"Test path does not exist: {candidate}")
        return candidate

    @staticmethod
    def _add_artifact(db, run_id: int, artifact_type: str, path: Path) -> None:
        relative = path.relative_to(ARTIFACT_ROOT)
        db.add(
            Artifact(
                run_id=run_id,
                artifact_type=artifact_type,
                file_name=path.name,
                file_path=f"/artifacts/{relative.as_posix()}",
            )
        )

    @staticmethod
    def _mark_internal_failure(run_id: int, exc: Exception) -> None:
        with SessionLocal() as db:
            run = db.get(TestRun, run_id)
            if run and run.status not in FINAL_STATUSES:
                run.status = "FAILED"
                run.finished_at = datetime.utcnow()
                run.error_message = f"{type(exc).__name__}: {exc}"[:4000]
                db.commit()


run_manager = RunManager()
