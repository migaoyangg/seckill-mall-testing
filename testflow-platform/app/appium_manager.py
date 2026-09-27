from __future__ import annotations

import json
import os
import subprocess
import time
from dataclasses import dataclass
from pathlib import Path
from urllib.error import URLError
from urllib.request import urlopen

from .config import WORKSPACE_ROOT


@dataclass
class AppiumHandle:
    url: str
    process: subprocess.Popen | None
    log_path: Path

    @property
    def owned(self) -> bool:
        return self.process is not None


def appium_is_ready(url: str) -> bool:
    try:
        with urlopen(f"{url.rstrip('/')}/status", timeout=2) as response:
            payload = json.loads(response.read().decode("utf-8"))
            return response.status == 200 and payload.get("value", {}).get("ready", True)
    except (URLError, TimeoutError, ValueError, OSError):
        return False


def resolve_appium_executable() -> Path:
    configured = os.getenv("TESTFLOW_APPIUM_BIN")
    candidates = [
        Path(configured).expanduser() if configured else None,
        WORKSPACE_ROOT / "android-ui-tests/node_modules/.bin/appium",
    ]
    for candidate in candidates:
        if candidate and candidate.is_file() and os.access(candidate, os.X_OK):
            return candidate.resolve()
    raise RuntimeError("Appium executable not found; install android-ui-tests npm dependencies or set TESTFLOW_APPIUM_BIN")


def ensure_appium(run_dir: Path, timeout_seconds: int = 35) -> AppiumHandle:
    url = os.getenv("TESTFLOW_APPIUM_URL", "http://127.0.0.1:4723")
    log_path = run_dir / "appium.log"
    if appium_is_ready(url):
        log_path.write_text("Reused an existing Appium server.\n", encoding="utf-8")
        return AppiumHandle(url=url, process=None, log_path=log_path)

    executable = resolve_appium_executable()
    log_file = log_path.open("w", encoding="utf-8")
    process = subprocess.Popen(
        [str(executable), "--address", "127.0.0.1", "--port", "4723"],
        cwd=WORKSPACE_ROOT / "android-ui-tests",
        stdout=log_file,
        stderr=subprocess.STDOUT,
        text=True,
        start_new_session=True,
    )
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        if process.poll() is not None:
            log_file.close()
            raise RuntimeError(f"Appium exited during startup with code {process.returncode}")
        if appium_is_ready(url):
            log_file.flush()
            return AppiumHandle(url=url, process=process, log_path=log_path)
        time.sleep(0.5)
    process.terminate()
    log_file.close()
    raise RuntimeError(f"Appium did not become ready within {timeout_seconds} seconds")


def stop_appium(handle: AppiumHandle | None) -> None:
    if not handle or not handle.process or handle.process.poll() is not None:
        return
    handle.process.terminate()
    try:
        handle.process.wait(timeout=8)
    except subprocess.TimeoutExpired:
        handle.process.kill()
        handle.process.wait(timeout=5)

