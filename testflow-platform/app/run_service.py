from __future__ import annotations

from datetime import datetime
from uuid import uuid4

from sqlalchemy.orm import Session

from .db import Environment, TestRun, TestSuite
from .runner import run_manager


def create_run_record(
    db: Session,
    environment: Environment,
    suite: TestSuite,
    created_by: str,
    retry_of_run_id: int | None = None,
) -> TestRun:
    now = datetime.utcnow()
    run_no = f"RUN-{now:%Y%m%d-%H%M%S}-{uuid4().hex[:6].upper()}"
    run = TestRun(
        run_no=run_no,
        project_id=suite.project_id,
        environment_id=environment.id,
        suite_id=suite.id,
        status="PENDING",
        created_by=created_by,
        retry_of_run_id=retry_of_run_id,
    )
    db.add(run)
    db.commit()
    db.refresh(run)
    run_manager.submit(run.id)
    return run

