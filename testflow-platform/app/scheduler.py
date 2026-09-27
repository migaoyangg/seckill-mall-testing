from __future__ import annotations

import threading
import time
from datetime import datetime, timedelta

from .db import Environment, SessionLocal, TestSchedule, TestSuite
from .run_service import create_run_record


class ScheduleManager:
    def __init__(self) -> None:
        self._thread: threading.Thread | None = None

    def start(self) -> None:
        if self._thread and self._thread.is_alive():
            return
        self._thread = threading.Thread(target=self._loop, name="testflow-scheduler", daemon=True)
        self._thread.start()

    def _loop(self) -> None:
        while True:
            self.trigger_due()
            time.sleep(5)

    def trigger_due(self) -> int:
        now = datetime.utcnow()
        triggered = 0
        with SessionLocal() as db:
            schedules = (
                db.query(TestSchedule)
                .filter(TestSchedule.enabled.is_(True), TestSchedule.next_run_at <= now)
                .all()
            )
            for schedule in schedules:
                environment = db.get(Environment, schedule.environment_id)
                suite = db.get(TestSuite, schedule.suite_id)
                if environment and suite and environment.project_id == suite.project_id:
                    create_run_record(db, environment, suite, f"schedule:{schedule.name}")
                    triggered += 1
                schedule.last_run_at = now
                schedule.next_run_at = now + timedelta(minutes=schedule.interval_minutes)
                db.commit()
        return triggered


schedule_manager = ScheduleManager()

