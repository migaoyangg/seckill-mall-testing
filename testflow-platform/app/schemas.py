from __future__ import annotations

from datetime import datetime
from typing import Any, Optional

from pydantic import BaseModel, ConfigDict, Field


class ORMModel(BaseModel):
    model_config = ConfigDict(from_attributes=True)


class ProjectCreate(BaseModel):
    name: str = Field(min_length=2, max_length=120)
    code: str = Field(pattern=r"^[a-zA-Z0-9_-]+$")
    repository_url: str = ""
    work_dir: str


class ProjectOut(ORMModel):
    id: int
    name: str
    code: str
    repository_url: str
    work_dir: str
    status: str
    created_at: datetime


class EnvironmentCreate(BaseModel):
    project_id: int
    name: str = Field(min_length=2, max_length=120)
    base_url: str = ""
    variables: dict[str, str] = Field(default_factory=dict)


class EnvironmentOut(ORMModel):
    id: int
    project_id: int
    name: str
    base_url: str
    variables_json: str
    status: str
    created_at: datetime


class SuiteCreate(BaseModel):
    project_id: int
    name: str = Field(min_length=2, max_length=160)
    test_type: str = Field(default="api", pattern=r"^(api|android|web|unit)$")
    test_path: str
    marker: str = Field(default="", pattern=r"^[a-zA-Z0-9_ ()&|!.-]*$")
    timeout_seconds: int = Field(default=900, ge=10, le=7200)
    device_required: bool = False


class SuiteOut(ORMModel):
    id: int
    project_id: int
    name: str
    test_type: str
    test_path: str
    marker: str
    timeout_seconds: int
    device_required: bool
    created_at: datetime


class RunCreate(BaseModel):
    environment_id: int
    suite_id: int
    created_by: str = Field(default="local-user", max_length=80)


class RunSummary(ORMModel):
    id: int
    run_no: str
    project_id: int
    environment_id: int
    suite_id: int
    status: str
    total_count: int
    passed_count: int
    failed_count: int
    skipped_count: int
    duration: float
    error_message: str
    created_by: str
    retry_of_run_id: Optional[int]
    created_at: datetime
    started_at: Optional[datetime]
    finished_at: Optional[datetime]


class CaseResultOut(ORMModel):
    case_name: str
    class_name: str
    status: str
    duration: float
    error_message: str


class ArtifactOut(ORMModel):
    artifact_type: str
    file_name: str
    file_path: str


class RunDetail(RunSummary):
    project_name: str
    environment_name: str
    suite_name: str
    case_results: list[CaseResultOut]
    artifacts: list[ArtifactOut]


class DashboardOut(BaseModel):
    total_runs: int
    passed_runs: int
    failed_runs: int
    running_runs: int
    average_pass_rate: float
    average_duration: float
    recent_runs: list[dict[str, Any]]
    frequent_failures: list[dict[str, Any]]


class ScheduleCreate(BaseModel):
    name: str = Field(min_length=2, max_length=160)
    environment_id: int
    suite_id: int
    interval_minutes: int = Field(ge=1, le=10080)
    created_by: str = Field(default="local-user", max_length=80)


class ScheduleOut(ORMModel):
    id: int
    name: str
    environment_id: int
    suite_id: int
    interval_minutes: int
    enabled: bool
    next_run_at: datetime
    last_run_at: Optional[datetime]
    created_by: str
    created_at: datetime


class LoginRequest(BaseModel):
    username: str = Field(min_length=1, max_length=80)
    password: str = Field(min_length=1, max_length=200)


class UserCreate(BaseModel):
    username: str = Field(pattern=r"^[a-zA-Z0-9_-]{3,40}$")
    display_name: str = Field(min_length=2, max_length=120)
    password: str = Field(min_length=8, max_length=200)
    role: str = Field(default="VIEWER", pattern=r"^(ADMIN|TESTER|VIEWER)$")


class UserOut(ORMModel):
    id: int
    username: str
    display_name: str
    role: str
    active: bool
    created_at: datetime


class LoginOut(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user: UserOut


class DeviceOut(ORMModel):
    id: int
    serial: str
    status: str
    model: str
    product: str
    android_version: str
    locked_by_run_id: Optional[int]
    last_seen_at: Optional[datetime]
