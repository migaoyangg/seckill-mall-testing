from pathlib import Path

from app.junit_parser import parse_junit


def test_parse_junit_counts_and_messages(tmp_path: Path):
    report = tmp_path / "junit.xml"
    report.write_text(
        """<?xml version="1.0" encoding="utf-8"?>
<testsuite tests="3" failures="1" skipped="1" time="0.6">
  <testcase classname="tests.test_demo" name="test_pass" time="0.1" />
  <testcase classname="tests.test_demo" name="test_fail" time="0.2"><failure message="expected true">trace</failure></testcase>
  <testcase classname="tests.test_demo" name="test_skip" time="0.3"><skipped message="not ready" /></testcase>
</testsuite>""",
        encoding="utf-8",
    )

    parsed = parse_junit(report)

    assert parsed.total == 3
    assert parsed.passed == 1
    assert parsed.failed == 1
    assert parsed.skipped == 1
    assert parsed.cases[1].error_message == "expected true"

