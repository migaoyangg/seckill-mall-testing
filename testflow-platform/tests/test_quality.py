import json
from types import SimpleNamespace

import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

from app import runner, main
from app.db import Base, CaseResult, DefectSubmission, Environment, Project, SystemUser, TestRun as RunRecord, TestSuite as SuiteRecord
from app.quality import compare_cases, stability_cases, validate_selectors


def case(name, status):
    return SimpleNamespace(node_id=name, class_name="", case_name=name, status=status)


def test_comparison_does_not_treat_missing_or_skipped_case_as_fixed():
    old = [case("fixed", "FAILED"), case("missing", "FAILED"), case("skipped", "FAILED"), case("new", "PASSED")]
    new = [case("fixed", "PASSED"), case("skipped", "SKIPPED"), case("new", "FAILED")]
    result = compare_cases(old, new)
    assert result["fixed"] == ["fixed"]
    assert result["not_executed"] == ["missing"]
    assert result["new_failures"] == ["new"]
    assert result["skipped"] == ["skipped"]


def test_stability_requires_three_executions_and_mixed_results():
    runs = [SimpleNamespace(id=i, case_results=[case("flaky", status)]) for i, status in enumerate(["PASSED", "FAILED", "PASSED"])]
    assert not stability_cases(runs[:2])
    assert stability_cases(runs)[0]["failures"] == 1


def test_precise_selector_rejects_path_escape(tmp_path):
    suite = tmp_path / "tests"
    suite.mkdir()
    test = suite / "test_demo.py"
    test.write_text("def test_ok(): pass")
    assert validate_selectors(["tests/test_demo.py::test_ok[param]"], tmp_path, suite)
    with pytest.raises(ValueError):
        validate_selectors(["../outside.py::test_bad"], tmp_path, suite)


def test_worker_runs_exact_failed_parameter_and_records_node_id(tmp_path, monkeypatch):
    suite_dir = tmp_path / "tests"
    suite_dir.mkdir()
    (suite_dir / "test_demo.py").write_text('import os\nimport pytest\n@pytest.mark.parametrize("value", [1, 2])\ndef test_demo(value):\n    assert value == 1 or os.getenv("FIXED") == "true"\n')
    engine = create_engine(f"sqlite:///{tmp_path / 'quality.db'}")
    Base.metadata.create_all(engine)
    sessions = sessionmaker(bind=engine, expire_on_commit=False)
    monkeypatch.setattr(runner, "SessionLocal", sessions)
    monkeypatch.setattr(runner, "WORKSPACE_ROOT", tmp_path)
    monkeypatch.setattr(runner, "ARTIFACT_ROOT", tmp_path / "artifacts")
    with sessions() as db:
        project = Project(name="Quality", code="quality", work_dir=str(tmp_path))
        db.add(project); db.flush()
        environment = Environment(project_id=project.id, name="test")
        suite = SuiteRecord(project_id=project.id, name="params", test_path="tests")
        db.add_all([environment, suite]); db.flush()
        first = RunRecord(run_no="RUN-FIRST", project_id=project.id, environment_id=environment.id, suite_id=suite.id)
        db.add(first); db.commit()
        first_id = first.id
    manager = runner.RunManager()
    manager._execute(first_id)
    with sessions() as db:
        first = db.get(RunRecord, first_id)
        assert first.total_count == 2
        failed = db.query(CaseResult).filter_by(run_id=first_id, status="FAILED").one()
        assert failed.node_id == "tests/test_demo.py::test_demo[2]"
        environment = db.get(Environment, first.environment_id)
        environment.variables_json = '{"FIXED":"true"}'
        second = RunRecord(run_no="RUN-RETRY", project_id=first.project_id, environment_id=first.environment_id,
                           suite_id=first.suite_id, retry_of_run_id=first_id, selected_nodeids_json=json.dumps([failed.node_id]))
        db.add(second); db.commit()
        second_id = second.id
    manager._execute(second_id)
    with sessions() as db:
        second = db.get(RunRecord, second_id)
        assert second.status == "PASSED"
        assert second.total_count == second.passed_count == 1
    engine.dispose()


def test_defect_regression_links_case_and_never_verifies_skipped_run(tmp_path, monkeypatch):
    (tmp_path / "test_demo.py").write_text("def test_failure(): pass")
    engine = create_engine("sqlite://")
    Base.metadata.create_all(engine)
    sessions = sessionmaker(bind=engine, expire_on_commit=False)
    monkeypatch.setattr(runner.run_manager, "submit", lambda *_: None)
    with sessions() as db:
        project = Project(name="Regression", code="regression", work_dir=str(tmp_path))
        user = SystemUser(username="tester", display_name="Tester", password_hash="x", role="TESTER")
        db.add_all([project, user]); db.flush()
        env = Environment(project_id=project.id, name="local")
        suite = SuiteRecord(project_id=project.id, name="suite", test_path="test_demo.py")
        db.add_all([env, suite]); db.flush()
        source = RunRecord(run_no="SOURCE", project_id=project.id, environment_id=env.id, suite_id=suite.id, status="FAILED")
        db.add(source); db.flush()
        failed = CaseResult(run_id=source.id, case_name="test_failure", node_id="test_demo.py::test_failure", status="FAILED")
        db.add(failed); db.flush()
        defect = DefectSubmission(run_id=source.id, case_result_id=failed.id, external_id="42", external_url="https://example.test/bug/42", title="Failure", submitted_by="tester")
        db.add(defect); db.commit()
        run = main.defect_regression(defect.id, db, user)
        assert run.regression_defect_id == defect.id
        assert json.loads(run.selected_nodeids_json) == [failed.node_id]
        run.status, run.total_count, run.skipped_count = "PASSED", 1, 1
        db.commit()
        assert not main.regression_history(defect.id, db, user)[0]["verified"]
        run.skipped_count, run.passed_count = 0, 1
        db.commit()
        assert main.regression_history(defect.id, db, user)[0]["verified"]
    engine.dispose()
