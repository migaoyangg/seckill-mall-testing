import json

import pytest
from fastapi import HTTPException
from urllib.error import URLError
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

from app.db import Base, CaseResult, DefectSubmission, Environment, Project, SystemUser, TestRun as RunRecord, TestSuite as SuiteRecord
from app.main import get_run, submit_defect
from app.schemas import DefectCreate
from app import defects


def test_confirmed_failed_case_creates_link_and_blocks_duplicate(monkeypatch):
    engine = create_engine("sqlite://")
    Base.metadata.create_all(engine)
    sessions = sessionmaker(bind=engine, expire_on_commit=False)
    with sessions() as db:
        project = Project(name="Defect test", code="defect-test", work_dir="/tmp")
        user = SystemUser(username="tester", display_name="Tester", password_hash="x", role="TESTER")
        db.add_all([project, user])
        db.flush()
        environment = Environment(project_id=project.id, name="local")
        suite = SuiteRecord(project_id=project.id, name="smoke", test_path="tests")
        db.add_all([environment, suite])
        db.flush()
        run = RunRecord(run_no="DEFECT-001", project_id=project.id, environment_id=environment.id, suite_id=suite.id, status="FAILED")
        db.add(run)
        db.flush()
        failed = CaseResult(run_id=run.id, case_name="test_checkout", status="FAILED")
        passed = CaseResult(run_id=run.id, case_name="test_login", status="PASSED")
        db.add_all([failed, passed])
        db.commit()
        run_id, failed_id, passed_id, user_id = run.id, failed.id, passed.id, user.id

    calls = []

    def fake_create(**kwargs):
        calls.append(kwargs)
        return "42", "https://zentao.example/bug-view-42.html"

    monkeypatch.setattr("app.main.create_zentao_bug", fake_create)
    payload = DefectCreate(case_result_id=failed_id, title="Checkout failure", steps="Steps and evidence", severity=2, priority=1)
    try:
        with sessions() as db:
            user = db.get(SystemUser, user_id)
            with pytest.raises(HTTPException) as wrong_case:
                submit_defect(run_id, payload.model_copy(update={"case_result_id": passed_id}), db, user)
            assert wrong_case.value.status_code == 400
            created = submit_defect(run_id, payload, db, user)
            assert created.external_id == "42"
            assert len(calls) == 1
            with pytest.raises(HTTPException) as duplicate:
                submit_defect(run_id, payload, db, user)
            assert duplicate.value.status_code == 409
            detail = get_run(run_id, db, user)
            assert detail.defects[0].case_result_id == failed_id
            assert db.query(DefectSubmission).count() == 1
    finally:
        engine.dispose()


def test_zentao_adapter_uses_server_side_token_and_v1_payload(monkeypatch):
    monkeypatch.setenv("TESTFLOW_ZENTAO_URL", "https://zentao.example")
    monkeypatch.setenv("TESTFLOW_ZENTAO_TOKEN", "private-token")
    monkeypatch.setenv("TESTFLOW_ZENTAO_PRODUCT_ID", "7")
    captured = {}

    class Response:
        def __enter__(self):
            return self

        def __exit__(self, *_):
            return False

        def read(self, *_):
            return b'{"id": 42, "status": "active"}'

    class Opener:
        def open(self, request, timeout):
            captured["request"] = request
            captured["timeout"] = timeout
            return Response()

    monkeypatch.setattr(defects, "build_opener", lambda *_: Opener())
    bug_id, bug_url = defects.create_zentao_bug(title="Checkout failure", steps="Reproduce", severity=2, priority=1)
    request = captured["request"]
    assert request.full_url == "https://zentao.example/api.php/v1/products/7/bugs"
    assert request.get_header("Token") == "private-token"
    assert json.loads(request.data)["title"] == "Checkout failure"
    assert captured["timeout"] == 10
    assert bug_id == "42"
    assert bug_url.endswith("/bug-view-42.html")


def test_zentao_adapter_rejects_missing_config_and_network_failure(monkeypatch):
    monkeypatch.delenv("TESTFLOW_ZENTAO_TOKEN", raising=False)
    assert not defects.zentao_configured()
    with pytest.raises(defects.DefectProviderError):
        defects.create_zentao_bug(title="Failure", steps="Reproduce", severity=3, priority=3)

    monkeypatch.setenv("TESTFLOW_ZENTAO_URL", "https://zentao.example")
    monkeypatch.setenv("TESTFLOW_ZENTAO_TOKEN", "private-token")
    monkeypatch.setenv("TESTFLOW_ZENTAO_PRODUCT_ID", "7")

    class FailedOpener:
        def open(self, *_args, **_kwargs):
            raise URLError("not reachable")

    monkeypatch.setattr(defects, "build_opener", lambda *_: FailedOpener())
    with pytest.raises(defects.DefectProviderError, match="request failed"):
        defects.create_zentao_bug(title="Failure", steps="Reproduce", severity=3, priority=3)
