from __future__ import annotations

import json
import os
import asyncio
import queue
from contextlib import asynccontextmanager
from datetime import datetime, timedelta
from pathlib import Path
from typing import Optional

from fastapi import Depends, FastAPI, HTTPException, WebSocket, WebSocketDisconnect
from fastapi.security import HTTPAuthorizationCredentials
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from sqlalchemy import desc, func
from sqlalchemy.orm import Session, selectinload

from .auth import authenticate_token, create_session, get_current_user, hash_password, require_roles, security, seed_admin, token_hash, verify_password
from .config import ARTIFACT_ROOT, BASE_DIR, WORKSPACE_ROOT
from .db import AndroidDevice, Artifact, AuthSession, CaseResult, Environment, Project, SessionLocal, SystemUser, TestRun, TestSchedule, TestSuite, get_db, init_db
from .devices import scan_devices
from .runner import FINAL_STATUSES, run_manager
from .log_stream import run_log_stream
from .run_service import create_run_record
from .scheduler import schedule_manager
from .schemas import (
    DashboardOut,
    DeviceOut,
    EnvironmentCreate,
    EnvironmentOut,
    LoginOut,
    LoginRequest,
    ProjectCreate,
    ProjectOut,
    RunCreate,
    RunDetail,
    RunSummary,
    ScheduleCreate,
    ScheduleOut,
    SuiteCreate,
    SuiteOut,
    UserCreate,
    UserOut,
)


def seed_demo_data() -> None:
    with SessionLocal() as db:
        project = db.query(Project).filter(Project.code == "testflow-self-test").first()
        if not project:
            project = Project(name="TestFlow 平台自检", code="testflow-self-test", repository_url="", work_dir=str(BASE_DIR))
            db.add(project)
            db.flush()
        if not db.query(Environment).filter(Environment.project_id == project.id).first():
            db.add(Environment(project_id=project.id, name="本地环境", base_url="", variables_json="{}"))
        if not db.query(TestSuite).filter(TestSuite.project_id == project.id).first():
            db.add(TestSuite(project_id=project.id, name="平台自检冒烟套件", test_type="unit", test_path="sample_tests", marker="smoke", timeout_seconds=120))

        mall = db.query(Project).filter(Project.code == "seckill-mall").first()
        if not mall:
            mall = Project(
                name="秒购商城自动化测试",
                code="seckill-mall",
                repository_url="https://github.com/migaoyangg/seckill-mall-testing",
                work_dir=str(WORKSPACE_ROOT),
            )
            db.add(mall)
            db.flush()
        mall_environment = db.query(Environment).filter(Environment.project_id == mall.id).first()
        if not mall_environment:
            mall_environment = Environment(
                project_id=mall.id,
                name="商城本地联调环境",
                base_url="http://127.0.0.1:8080/api",
                variables_json=json.dumps({"TEST_API_BASE_URL": "http://127.0.0.1:8080/api", "RUN_MUTATING": "true"}),
            )
            db.add(mall_environment)
        existing_names = {item.name for item in db.query(TestSuite).filter(TestSuite.project_id == mall.id).all()}
        if "商城接口冒烟" not in existing_names:
            db.add(TestSuite(project_id=mall.id, name="商城接口冒烟", test_type="api", test_path="tests/api", marker="smoke", timeout_seconds=600))
        if "Android UI 全量回归" not in existing_names:
            db.add(TestSuite(project_id=mall.id, name="Android UI 全量回归", test_type="android", test_path="android-ui-tests", marker="", timeout_seconds=1800, device_required=True))
        db.commit()


@asynccontextmanager
async def lifespan(_: FastAPI):
    init_db()
    seed_admin()
    seed_demo_data()
    if os.getenv("TESTFLOW_EMBEDDED_WORKER", "true").lower() == "true":
        run_manager.start()
        run_manager.recover_pending()
    schedule_manager.start()
    yield


app = FastAPI(
    title="TestFlow 测试任务调度与质量分析平台",
    version="0.1.0",
    lifespan=lifespan,
)
app.mount("/artifacts", StaticFiles(directory=ARTIFACT_ROOT), name="artifacts")
app.mount("/static", StaticFiles(directory=BASE_DIR / "static"), name="static")


@app.get("/", include_in_schema=False)
def index():
    return FileResponse(BASE_DIR / "static" / "index.html")


@app.get("/api/health")
def health():
    return {"status": "UP", "time": datetime.utcnow().isoformat()}


@app.post("/api/auth/login", response_model=LoginOut)
def login(payload: LoginRequest, db: Session = Depends(get_db)):
    user = db.query(SystemUser).filter(SystemUser.username == payload.username).first()
    if not user or not user.active or not verify_password(payload.password, user.password_hash):
        raise HTTPException(401, "Invalid username or password")
    token = create_session(db, user)
    return LoginOut(access_token=token, user=user)


@app.get("/api/auth/me", response_model=UserOut)
def current_user(user: SystemUser = Depends(get_current_user)):
    return user


@app.post("/api/auth/logout")
def logout(
    credentials: HTTPAuthorizationCredentials = Depends(security),
    db: Session = Depends(get_db),
    _: SystemUser = Depends(get_current_user),
):
    db.query(AuthSession).filter(AuthSession.token_hash == token_hash(credentials.credentials)).delete()
    db.commit()
    return {"status": "LOGGED_OUT"}


@app.get("/api/users", response_model=list[UserOut])
def list_users(db: Session = Depends(get_db), _: SystemUser = Depends(require_roles("ADMIN"))):
    return db.query(SystemUser).order_by(SystemUser.id).all()


@app.post("/api/users", response_model=UserOut, status_code=201)
def create_user(payload: UserCreate, db: Session = Depends(get_db), _: SystemUser = Depends(require_roles("ADMIN"))):
    if db.query(SystemUser).filter(SystemUser.username == payload.username).first():
        raise HTTPException(409, "Username already exists")
    user = SystemUser(
        username=payload.username,
        display_name=payload.display_name,
        password_hash=hash_password(payload.password),
        role=payload.role,
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    return user


def require_project(db: Session, project_id: int) -> Project:
    project = db.get(Project, project_id)
    if not project:
        raise HTTPException(404, "Project not found")
    return project


@app.get("/api/projects", response_model=list[ProjectOut])
def list_projects(db: Session = Depends(get_db), _: SystemUser = Depends(require_roles("ADMIN", "TESTER", "VIEWER"))):
    return db.query(Project).order_by(Project.id).all()


@app.post("/api/projects", response_model=ProjectOut, status_code=201)
def create_project(payload: ProjectCreate, db: Session = Depends(get_db), _: SystemUser = Depends(require_roles("ADMIN"))):
    work_dir = Path(payload.work_dir).expanduser().resolve()
    if work_dir != WORKSPACE_ROOT and WORKSPACE_ROOT not in work_dir.parents:
        raise HTTPException(400, "work_dir must stay inside the workspace")
    if not work_dir.is_dir():
        raise HTTPException(400, "work_dir does not exist")
    if db.query(Project).filter((Project.code == payload.code) | (Project.name == payload.name)).first():
        raise HTTPException(409, "Project name or code already exists")
    project = Project(**payload.model_dump(), work_dir=str(work_dir))
    db.add(project)
    db.commit()
    db.refresh(project)
    return project


@app.get("/api/environments", response_model=list[EnvironmentOut])
def list_environments(project_id: Optional[int] = None, db: Session = Depends(get_db), _: SystemUser = Depends(require_roles("ADMIN", "TESTER", "VIEWER"))):
    query = db.query(Environment)
    if project_id:
        query = query.filter(Environment.project_id == project_id)
    return query.order_by(Environment.id).all()


@app.post("/api/environments", response_model=EnvironmentOut, status_code=201)
def create_environment(payload: EnvironmentCreate, db: Session = Depends(get_db), _: SystemUser = Depends(require_roles("ADMIN"))):
    require_project(db, payload.project_id)
    environment = Environment(
        project_id=payload.project_id,
        name=payload.name,
        base_url=payload.base_url,
        variables_json=json.dumps(payload.variables, ensure_ascii=False),
    )
    db.add(environment)
    db.commit()
    db.refresh(environment)
    return environment


@app.get("/api/suites", response_model=list[SuiteOut])
def list_suites(project_id: Optional[int] = None, db: Session = Depends(get_db), _: SystemUser = Depends(require_roles("ADMIN", "TESTER", "VIEWER"))):
    query = db.query(TestSuite)
    if project_id:
        query = query.filter(TestSuite.project_id == project_id)
    return query.order_by(TestSuite.id).all()


@app.post("/api/suites", response_model=SuiteOut, status_code=201)
def create_suite(payload: SuiteCreate, db: Session = Depends(get_db), _: SystemUser = Depends(require_roles("ADMIN"))):
    project = require_project(db, payload.project_id)
    work_dir = Path(project.work_dir).resolve()
    test_path = (work_dir / payload.test_path).resolve()
    if test_path != work_dir and work_dir not in test_path.parents:
        raise HTTPException(400, "test_path escapes project work_dir")
    if not test_path.exists():
        raise HTTPException(400, "test_path does not exist")
    suite = TestSuite(**payload.model_dump())
    db.add(suite)
    db.commit()
    db.refresh(suite)
    return suite


@app.get("/api/runs", response_model=list[RunSummary])
def list_runs(limit: int = 50, db: Session = Depends(get_db), _: SystemUser = Depends(require_roles("ADMIN", "TESTER", "VIEWER"))):
    return db.query(TestRun).order_by(desc(TestRun.id)).limit(min(max(limit, 1), 200)).all()


@app.post("/api/runs", response_model=RunSummary, status_code=202)
def create_run(payload: RunCreate, db: Session = Depends(get_db), _: SystemUser = Depends(require_roles("ADMIN", "TESTER"))):
    environment = db.get(Environment, payload.environment_id)
    suite = db.get(TestSuite, payload.suite_id)
    if not environment or not suite:
        raise HTTPException(404, "Environment or suite not found")
    if environment.project_id != suite.project_id:
        raise HTTPException(400, "Environment and suite belong to different projects")
    return create_run_record(db, environment, suite, payload.created_by)


@app.get("/api/runs/{run_id}", response_model=RunDetail)
def get_run(run_id: int, db: Session = Depends(get_db), _: SystemUser = Depends(require_roles("ADMIN", "TESTER", "VIEWER"))):
    run = (
        db.query(TestRun)
        .options(selectinload(TestRun.case_results), selectinload(TestRun.artifacts))
        .filter(TestRun.id == run_id)
        .first()
    )
    if not run:
        raise HTTPException(404, "Run not found")
    return RunDetail(
        **RunSummary.model_validate(run).model_dump(),
        project_name=run.project.name,
        environment_name=run.environment.name,
        suite_name=run.suite.name,
        case_results=run.case_results,
        artifacts=run.artifacts,
    )


@app.get("/api/runs/{run_id}/log")
def get_run_log(run_id: int, db: Session = Depends(get_db), _: SystemUser = Depends(require_roles("ADMIN", "TESTER", "VIEWER"))):
    run = db.get(TestRun, run_id)
    if not run:
        raise HTTPException(404, "Run not found")
    log_path = ARTIFACT_ROOT / run.run_no / "execution.log"
    content = log_path.read_text(encoding="utf-8", errors="replace") if log_path.exists() else ""
    return {"run_id": run_id, "content": content[-100_000:]}


@app.websocket("/ws/runs/{run_id}/logs")
async def run_logs(websocket: WebSocket, run_id: int, token: str = ""):
    with SessionLocal() as db:
        user = authenticate_token(db, token)
        run = db.get(TestRun, run_id)
        if not user or not run:
            await websocket.close(code=4401)
            return
        run_no = run.run_no
    await websocket.accept()
    log_path = ARTIFACT_ROOT / run_no / "execution.log"
    if log_path.exists():
        await websocket.send_json({"type": "history", "content": log_path.read_text(encoding="utf-8", errors="replace")[-100_000:]})
    channel = run_log_stream.subscribe(run_id)
    idle_ticks = 0
    try:
        while True:
            try:
                line = channel.get_nowait()
                await websocket.send_json({"type": "line", "content": line})
                idle_ticks = 0
            except queue.Empty:
                await asyncio.sleep(0.25)
                idle_ticks += 1
                if idle_ticks >= 20:
                    await websocket.send_json({"type": "heartbeat"})
                    idle_ticks = 0
    except (WebSocketDisconnect, RuntimeError):
        pass
    finally:
        run_log_stream.unsubscribe(run_id, channel)


@app.post("/api/runs/{run_id}/cancel")
def cancel_run(run_id: int, db: Session = Depends(get_db), _: SystemUser = Depends(require_roles("ADMIN", "TESTER"))):
    run = db.get(TestRun, run_id)
    if not run:
        raise HTTPException(404, "Run not found")
    if run.status in FINAL_STATUSES:
        raise HTTPException(409, f"Run is already {run.status}")
    if not run_manager.cancel(run_id):
        raise HTTPException(409, "Run could not be cancelled")
    return {"id": run_id, "status": "CANCELLED"}


@app.post("/api/runs/{run_id}/retry", response_model=RunSummary, status_code=202)
def retry_run(run_id: int, db: Session = Depends(get_db), _: SystemUser = Depends(require_roles("ADMIN", "TESTER"))):
    previous = db.get(TestRun, run_id)
    if not previous:
        raise HTTPException(404, "Run not found")
    if previous.status not in {"FAILED", "TIMEOUT", "CANCELLED"}:
        raise HTTPException(409, "Only failed, timed out, or cancelled runs can be retried")
    environment = db.get(Environment, previous.environment_id)
    suite = db.get(TestSuite, previous.suite_id)
    if not environment or not suite:
        raise HTTPException(409, "Original environment or suite no longer exists")
    return create_run_record(db, environment, suite, previous.created_by, retry_of_run_id=previous.id)


@app.get("/api/schedules", response_model=list[ScheduleOut])
def list_schedules(db: Session = Depends(get_db), _: SystemUser = Depends(require_roles("ADMIN", "TESTER", "VIEWER"))):
    return db.query(TestSchedule).order_by(TestSchedule.id).all()


@app.post("/api/schedules", response_model=ScheduleOut, status_code=201)
def create_schedule(payload: ScheduleCreate, db: Session = Depends(get_db), _: SystemUser = Depends(require_roles("ADMIN", "TESTER"))):
    environment = db.get(Environment, payload.environment_id)
    suite = db.get(TestSuite, payload.suite_id)
    if not environment or not suite:
        raise HTTPException(404, "Environment or suite not found")
    if environment.project_id != suite.project_id:
        raise HTTPException(400, "Environment and suite belong to different projects")
    schedule = TestSchedule(
        name=payload.name,
        environment_id=payload.environment_id,
        suite_id=payload.suite_id,
        interval_minutes=payload.interval_minutes,
        enabled=True,
        next_run_at=datetime.utcnow() + timedelta(minutes=payload.interval_minutes),
        created_by=payload.created_by,
    )
    db.add(schedule)
    db.commit()
    db.refresh(schedule)
    return schedule


@app.post("/api/schedules/{schedule_id}/toggle", response_model=ScheduleOut)
def toggle_schedule(schedule_id: int, db: Session = Depends(get_db), _: SystemUser = Depends(require_roles("ADMIN", "TESTER"))):
    schedule = db.get(TestSchedule, schedule_id)
    if not schedule:
        raise HTTPException(404, "Schedule not found")
    schedule.enabled = not schedule.enabled
    if schedule.enabled and schedule.next_run_at < datetime.utcnow():
        schedule.next_run_at = datetime.utcnow() + timedelta(minutes=schedule.interval_minutes)
    db.commit()
    db.refresh(schedule)
    return schedule


@app.get("/api/dashboard", response_model=DashboardOut)
def dashboard(db: Session = Depends(get_db), _: SystemUser = Depends(require_roles("ADMIN", "TESTER", "VIEWER"))):
    runs = db.query(TestRun).order_by(desc(TestRun.id)).limit(100).all()
    completed = [run for run in runs if run.status in {"PASSED", "FAILED", "TIMEOUT"}]
    pass_rates = [run.passed_count / run.total_count * 100 for run in completed if run.total_count]
    durations = [run.duration for run in completed if run.duration]
    recent_runs = [
        {
            "id": run.id,
            "run_no": run.run_no,
            "status": run.status,
            "pass_rate": round(run.passed_count / run.total_count * 100, 2) if run.total_count else 0,
            "duration": run.duration,
            "created_at": run.created_at.isoformat(),
        }
        for run in runs[:10]
    ]
    failures = (
        db.query(CaseResult.case_name, func.count(CaseResult.id).label("count"))
        .filter(CaseResult.status == "FAILED")
        .group_by(CaseResult.case_name)
        .order_by(desc("count"))
        .limit(8)
        .all()
    )
    return DashboardOut(
        total_runs=len(runs),
        passed_runs=sum(run.status == "PASSED" for run in runs),
        failed_runs=sum(run.status in {"FAILED", "TIMEOUT"} for run in runs),
        running_runs=sum(run.status in {"PENDING", "RUNNING"} for run in runs),
        average_pass_rate=round(sum(pass_rates) / len(pass_rates), 2) if pass_rates else 0,
        average_duration=round(sum(durations) / len(durations), 2) if durations else 0,
        recent_runs=recent_runs,
        frequent_failures=[{"case_name": name, "count": count} for name, count in failures],
    )


@app.get("/api/devices", response_model=list[DeviceOut])
def list_devices(db: Session = Depends(get_db), _: SystemUser = Depends(require_roles("ADMIN", "TESTER", "VIEWER"))):
    return db.query(AndroidDevice).order_by(AndroidDevice.serial).all()


@app.post("/api/devices/scan", response_model=list[DeviceOut])
def refresh_devices(_: SystemUser = Depends(require_roles("ADMIN", "TESTER"))):
    try:
        return scan_devices()
    except (RuntimeError, OSError) as exc:
        raise HTTPException(503, str(exc)) from exc
