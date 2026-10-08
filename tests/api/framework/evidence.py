"""Record business assertions without credentials or raw HTTP payloads."""

import json
from pathlib import Path
import time


class BusinessEvidence:
    def __init__(self, case_id, entries):
        self.case_id = case_id
        self.entries = entries

    def record(self, phase, check, expected, actual, passed=True):
        self.entries.append({"case_id": self.case_id, "phase": phase, "check": check,
                             "expected": expected, "actual": actual, "status": "PASSED" if passed else "FAILED"})

    def equal(self, check, actual, expected):
        self.record("assertion", check, expected, actual, actual == expected)
        assert actual == expected, f"{check}: expected={expected!r}, actual={actual!r}"


def wait_until(fetch, predicate, *, timeout=5, interval=0.1):
    """Poll asynchronous persistence with a bounded deadline and latest evidence."""
    deadline = time.monotonic() + timeout
    while True:
        value = fetch()
        if predicate(value):
            return value
        if time.monotonic() >= deadline:
            raise AssertionError(f"异步状态等待超时，最后结果: {value!r}")
        time.sleep(interval)


def save_evidence(path, entries):
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_suffix(".tmp")
    temporary.write_text(json.dumps({"version": 1, "checks": entries}, ensure_ascii=False, indent=2), encoding="utf-8")
    temporary.replace(target)
