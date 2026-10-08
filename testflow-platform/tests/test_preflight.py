from types import SimpleNamespace

from app.preflight import inspect_environment


def test_preflight_reports_missing_credentials_without_values(tmp_path, monkeypatch):
    (tmp_path / "tests/api").mkdir(parents=True)
    names = ["API_TEST_USERNAME", "API_TEST_PASSWORD", "API_TEST_ADMIN_USERNAME", "API_TEST_ADMIN_PASSWORD", "API_TEST_MYSQL_PASSWORD"]
    for name in names:
        monkeypatch.delenv(name, raising=False)
    project = SimpleNamespace(work_dir=str(tmp_path), code="seckill-mall")
    suite = SimpleNamespace(test_path="tests/api", device_required=False)
    environment = SimpleNamespace(base_url="", variables_json='{"API_TEST_DATA_VALIDATION":"true"}')
    result = inspect_environment(project, suite, environment)
    assert not result["ready"]
    assert "API_TEST_MYSQL_PASSWORD" in result["checks"][-1]["detail"]


def test_preflight_accepts_self_test_without_business_service(tmp_path):
    (tmp_path / "tests").mkdir()
    result = inspect_environment(SimpleNamespace(work_dir=str(tmp_path), code="demo"),
                                 SimpleNamespace(test_path="tests", device_required=False),
                                 SimpleNamespace(base_url="", variables_json="{}"))
    assert result["ready"]
