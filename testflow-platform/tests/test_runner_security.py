from pathlib import Path

import pytest

from app.runner import RunManager


def test_test_path_cannot_escape_work_dir(tmp_path: Path):
    work_dir = tmp_path / "project"
    work_dir.mkdir()
    outside = tmp_path / "outside"
    outside.mkdir()

    with pytest.raises(ValueError, match="escapes"):
        RunManager._safe_test_path(work_dir, "../outside")


def test_existing_test_path_is_allowed(tmp_path: Path):
    work_dir = tmp_path / "project"
    test_dir = work_dir / "tests"
    test_dir.mkdir(parents=True)

    assert RunManager._safe_test_path(work_dir, "tests") == test_dir.resolve()


def test_suite_local_virtualenv_is_preferred(tmp_path: Path):
    suite = tmp_path / "android-suite"
    executable = suite / ".venv" / "bin" / "python"
    executable.parent.mkdir(parents=True)
    executable.write_text("#!/bin/sh\n", encoding="utf-8")
    executable.chmod(0o755)

    assert RunManager._python_executable_for(suite) == str(executable.absolute())
