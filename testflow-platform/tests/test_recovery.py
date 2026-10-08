from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

from app import runner
from app.broker import LocalTaskBroker
from app.db import AndroidDevice, Base, Environment, Project, TestRun as RunRecord, TestSuite as SuiteRecord


def test_restart_releases_device_locked_by_interrupted_run(tmp_path, monkeypatch):
    engine = create_engine(f"sqlite:///{tmp_path / 'recovery.db'}")
    Base.metadata.create_all(engine)
    test_session = sessionmaker(bind=engine, expire_on_commit=False)
    monkeypatch.setattr(runner, "SessionLocal", test_session)

    with test_session() as db:
        project = Project(name="Recovery test", code="recovery-test", work_dir=str(tmp_path))
        db.add(project)
        db.flush()
        environment = Environment(project_id=project.id, name="local")
        suite = SuiteRecord(project_id=project.id, name="Android", test_path="tests", device_required=True)
        db.add_all([environment, suite])
        db.flush()
        run = RunRecord(
            run_no="RUN-RECOVERY-001",
            project_id=project.id,
            environment_id=environment.id,
            suite_id=suite.id,
            status="RUNNING",
        )
        db.add(run)
        db.flush()
        db.add(AndroidDevice(serial="emulator-5554", status="ONLINE", locked_by_run_id=run.id))
        db.commit()
        run_id = run.id

    runner.RunManager(broker=LocalTaskBroker()).recover_pending()

    with test_session() as db:
        recovered = db.get(RunRecord, run_id)
        device = db.query(AndroidDevice).filter_by(serial="emulator-5554").one()
        assert recovered.status == "FAILED"
        assert device.locked_by_run_id is None
