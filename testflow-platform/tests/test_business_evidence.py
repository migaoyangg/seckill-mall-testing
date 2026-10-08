import json
from types import SimpleNamespace

import pytest
from fastapi import HTTPException

from app import main


def test_evidence_endpoint_handles_missing_valid_and_invalid_manifest(tmp_path, monkeypatch):
    monkeypatch.setattr(main, "ARTIFACT_ROOT", tmp_path)
    run_dir = tmp_path / "RUN-EVIDENCE"
    run_dir.mkdir()
    db = SimpleNamespace(get=lambda *_: SimpleNamespace(run_no="RUN-EVIDENCE"))
    assert main.get_business_evidence(1, db, None)["checks"] == []
    path = run_dir / "business-evidence.json"
    path.write_text(json.dumps({"version": 1, "checks": [{"check": "stock", "actual": 250, "expected": 0, "status": "FAILED"}]}))
    assert main.get_business_evidence(1, db, None)["checks"][0]["actual"] == 250
    path.write_text("not-json")
    with pytest.raises(HTTPException) as error:
        main.get_business_evidence(1, db, None)
    assert error.value.status_code == 422
