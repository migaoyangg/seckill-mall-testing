import time

import pytest


@pytest.mark.smoke
def test_task_configuration_loaded():
    assert True


@pytest.mark.smoke
def test_environment_is_isolated(tmp_path):
    marker = tmp_path / "isolated.txt"
    marker.write_text("testflow", encoding="utf-8")
    assert marker.read_text(encoding="utf-8") == "testflow"


@pytest.mark.smoke
@pytest.mark.parametrize("status", ["PENDING", "RUNNING", "PASSED"])
def test_supported_status(status):
    assert status in {"PENDING", "RUNNING", "PASSED", "FAILED", "TIMEOUT", "CANCELLED"}


@pytest.mark.slow
def test_cancellable_workload():
    time.sleep(3)
    assert True

