from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from xml.etree import ElementTree


@dataclass
class ParsedCase:
    name: str
    class_name: str
    status: str
    duration: float
    error_message: str


@dataclass
class ParsedReport:
    total: int
    passed: int
    failed: int
    skipped: int
    duration: float
    cases: list[ParsedCase]


def parse_junit(path: Path) -> ParsedReport:
    root = ElementTree.parse(path).getroot()
    suites = [root] if root.tag.endswith("testsuite") else list(root.findall(".//testsuite"))
    cases: list[ParsedCase] = []

    for suite in suites:
        for node in suite.findall("testcase"):
            failure = node.find("failure")
            error = node.find("error")
            skipped = node.find("skipped")
            if failure is not None or error is not None:
                problem = failure if failure is not None else error
                status = "FAILED"
                message = (problem.get("message") or problem.text or "Test failed").strip()
            elif skipped is not None:
                status = "SKIPPED"
                message = (skipped.get("message") or skipped.text or "Skipped").strip()
            else:
                status = "PASSED"
                message = ""
            cases.append(
                ParsedCase(
                    name=node.get("name", "unknown"),
                    class_name=node.get("classname", ""),
                    status=status,
                    duration=float(node.get("time", "0") or 0),
                    error_message=message[:4000],
                )
            )

    passed = sum(case.status == "PASSED" for case in cases)
    failed = sum(case.status == "FAILED" for case in cases)
    skipped = sum(case.status == "SKIPPED" for case in cases)
    return ParsedReport(
        total=len(cases),
        passed=passed,
        failed=failed,
        skipped=skipped,
        duration=sum(case.duration for case in cases),
        cases=cases,
    )

