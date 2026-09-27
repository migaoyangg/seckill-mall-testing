from __future__ import annotations

from datetime import datetime
from typing import Optional

from sqlalchemy import Boolean, DateTime, Float, ForeignKey, Integer, String, Text, create_engine, inspect, text
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, relationship, sessionmaker

from .config import DATABASE_URL


class Base(DeclarativeBase):
    pass


class Project(Base):
    __tablename__ = "test_project"

    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(120), unique=True)
    code: Mapped[str] = mapped_column(String(80), unique=True, index=True)
    repository_url: Mapped[str] = mapped_column(String(500), default="")
    work_dir: Mapped[str] = mapped_column(String(1000))
    status: Mapped[str] = mapped_column(String(20), default="ACTIVE")
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)

    environments: Mapped[list[Environment]] = relationship(back_populates="project", cascade="all, delete-orphan")
    suites: Mapped[list[TestSuite]] = relationship(back_populates="project", cascade="all, delete-orphan")


class Environment(Base):
    __tablename__ = "test_environment"

    id: Mapped[int] = mapped_column(primary_key=True)
    project_id: Mapped[int] = mapped_column(ForeignKey("test_project.id"), index=True)
    name: Mapped[str] = mapped_column(String(120))
    base_url: Mapped[str] = mapped_column(String(500), default="")
    variables_json: Mapped[str] = mapped_column(Text, default="{}")
    status: Mapped[str] = mapped_column(String(20), default="ACTIVE")
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)

    project: Mapped[Project] = relationship(back_populates="environments")


class TestSuite(Base):
    __tablename__ = "test_suite"

    id: Mapped[int] = mapped_column(primary_key=True)
    project_id: Mapped[int] = mapped_column(ForeignKey("test_project.id"), index=True)
    name: Mapped[str] = mapped_column(String(160))
    test_type: Mapped[str] = mapped_column(String(30), default="api")
    test_path: Mapped[str] = mapped_column(String(1000))
    marker: Mapped[str] = mapped_column(String(120), default="")
    timeout_seconds: Mapped[int] = mapped_column(Integer, default=900)
    device_required: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)

    project: Mapped[Project] = relationship(back_populates="suites")


class TestRun(Base):
    __tablename__ = "test_run"

    id: Mapped[int] = mapped_column(primary_key=True)
    run_no: Mapped[str] = mapped_column(String(60), unique=True, index=True)
    project_id: Mapped[int] = mapped_column(ForeignKey("test_project.id"), index=True)
    environment_id: Mapped[int] = mapped_column(ForeignKey("test_environment.id"), index=True)
    suite_id: Mapped[int] = mapped_column(ForeignKey("test_suite.id"), index=True)
    status: Mapped[str] = mapped_column(String(20), default="PENDING", index=True)
    total_count: Mapped[int] = mapped_column(Integer, default=0)
    passed_count: Mapped[int] = mapped_column(Integer, default=0)
    failed_count: Mapped[int] = mapped_column(Integer, default=0)
    skipped_count: Mapped[int] = mapped_column(Integer, default=0)
    duration: Mapped[float] = mapped_column(Float, default=0)
    error_message: Mapped[str] = mapped_column(Text, default="")
    created_by: Mapped[str] = mapped_column(String(80), default="local-user")
    retry_of_run_id: Mapped[Optional[int]] = mapped_column(ForeignKey("test_run.id"), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)
    started_at: Mapped[Optional[datetime]] = mapped_column(DateTime, nullable=True)
    finished_at: Mapped[Optional[datetime]] = mapped_column(DateTime, nullable=True)

    project: Mapped[Project] = relationship()
    environment: Mapped[Environment] = relationship()
    suite: Mapped[TestSuite] = relationship()
    case_results: Mapped[list[CaseResult]] = relationship(cascade="all, delete-orphan")
    artifacts: Mapped[list[Artifact]] = relationship(cascade="all, delete-orphan")


class CaseResult(Base):
    __tablename__ = "test_case_result"

    id: Mapped[int] = mapped_column(primary_key=True)
    run_id: Mapped[int] = mapped_column(ForeignKey("test_run.id"), index=True)
    case_name: Mapped[str] = mapped_column(String(500))
    class_name: Mapped[str] = mapped_column(String(500), default="")
    status: Mapped[str] = mapped_column(String(20))
    duration: Mapped[float] = mapped_column(Float, default=0)
    error_message: Mapped[str] = mapped_column(Text, default="")


class Artifact(Base):
    __tablename__ = "test_artifact"

    id: Mapped[int] = mapped_column(primary_key=True)
    run_id: Mapped[int] = mapped_column(ForeignKey("test_run.id"), index=True)
    artifact_type: Mapped[str] = mapped_column(String(40))
    file_name: Mapped[str] = mapped_column(String(255))
    file_path: Mapped[str] = mapped_column(String(1000))
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)


class TestSchedule(Base):
    __tablename__ = "test_schedule"

    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(160))
    environment_id: Mapped[int] = mapped_column(ForeignKey("test_environment.id"), index=True)
    suite_id: Mapped[int] = mapped_column(ForeignKey("test_suite.id"), index=True)
    interval_minutes: Mapped[int] = mapped_column(Integer)
    enabled: Mapped[bool] = mapped_column(Boolean, default=True)
    next_run_at: Mapped[datetime] = mapped_column(DateTime)
    last_run_at: Mapped[Optional[datetime]] = mapped_column(DateTime, nullable=True)
    created_by: Mapped[str] = mapped_column(String(80), default="local-user")
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)

    environment: Mapped[Environment] = relationship()
    suite: Mapped[TestSuite] = relationship()


class SystemUser(Base):
    __tablename__ = "system_user"

    id: Mapped[int] = mapped_column(primary_key=True)
    username: Mapped[str] = mapped_column(String(80), unique=True, index=True)
    display_name: Mapped[str] = mapped_column(String(120))
    password_hash: Mapped[str] = mapped_column(String(500))
    role: Mapped[str] = mapped_column(String(20), default="VIEWER")
    active: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)


class AuthSession(Base):
    __tablename__ = "auth_session"

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("system_user.id"), index=True)
    token_hash: Mapped[str] = mapped_column(String(64), unique=True, index=True)
    expires_at: Mapped[datetime] = mapped_column(DateTime, index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)

    user: Mapped[SystemUser] = relationship()


class AndroidDevice(Base):
    __tablename__ = "android_device"

    id: Mapped[int] = mapped_column(primary_key=True)
    serial: Mapped[str] = mapped_column(String(160), unique=True, index=True)
    status: Mapped[str] = mapped_column(String(30), default="OFFLINE", index=True)
    model: Mapped[str] = mapped_column(String(160), default="")
    product: Mapped[str] = mapped_column(String(160), default="")
    android_version: Mapped[str] = mapped_column(String(50), default="")
    locked_by_run_id: Mapped[Optional[int]] = mapped_column(ForeignKey("test_run.id"), nullable=True, index=True)
    last_seen_at: Mapped[Optional[datetime]] = mapped_column(DateTime, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)


connect_args = {"check_same_thread": False} if DATABASE_URL.startswith("sqlite") else {}
engine = create_engine(DATABASE_URL, connect_args=connect_args)
SessionLocal = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)


def init_db() -> None:
    Base.metadata.create_all(bind=engine)
    columns = {column["name"] for column in inspect(engine).get_columns("test_run")}
    if "retry_of_run_id" not in columns:
        with engine.begin() as connection:
            connection.execute(text("ALTER TABLE test_run ADD COLUMN retry_of_run_id INTEGER"))


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
