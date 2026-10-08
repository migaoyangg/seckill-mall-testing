"""Comparable-run analysis and safe exact pytest case selection."""

from pathlib import Path


def case_key(case):
    return case.node_id or f"{case.class_name}::{case.case_name}"


def compare_cases(before, after):
    old = {case_key(case): case.status for case in before}
    new = {case_key(case): case.status for case in after}
    return {
        "new_failures": [key for key, status in new.items() if status == "FAILED" and old.get(key) != "FAILED"],
        "fixed": [key for key, status in new.items() if status == "PASSED" and old.get(key) == "FAILED"],
        "persistent_failures": [key for key, status in new.items() if status == "FAILED" and old.get(key) == "FAILED"],
        "added": sorted(new.keys() - old.keys()),
        "not_executed": sorted(old.keys() - new.keys()),
        "skipped": [key for key, status in new.items() if status == "SKIPPED"],
    }


def stability_cases(runs):
    histories = {}
    for run in runs:
        for case in run.case_results:
            if case.status in {"PASSED", "FAILED"}:
                histories.setdefault(case_key(case), []).append({"run_id": run.id, "status": case.status})
    return [{"case": key, "executions": len(history), "failures": sum(item["status"] == "FAILED" for item in history),
             "history": history, "suspected_unstable": len(history) >= 3 and len({item["status"] for item in history}) > 1}
            for key, history in histories.items() if len(history) >= 3 and len({item["status"] for item in history}) > 1]


def validate_selectors(nodeids, work_dir, suite_path):
    root, allowed = Path(work_dir).resolve(), Path(suite_path).resolve()
    if not nodeids or len(nodeids) > 200:
        raise ValueError("Exact retry requires 1-200 recorded pytest node IDs")
    for nodeid in nodeids:
        if not isinstance(nodeid, str) or nodeid.startswith("-") or "::" not in nodeid or any(char in nodeid for char in ("\n", "\r", "\x00")):
            raise ValueError("Invalid pytest node ID")
        path = (root / nodeid.split("::", 1)[0]).resolve()
        if path != allowed and allowed not in path.parents:
            raise ValueError("Selected case escapes suite path")
        if not path.is_file() or path.suffix != ".py":
            raise ValueError("Selected test file no longer exists")
    return list(dict.fromkeys(nodeids))
