from __future__ import annotations

import os
from pathlib import Path


BASE_DIR = Path(__file__).resolve().parents[1]
WORKSPACE_ROOT = BASE_DIR.parent.resolve()
DATA_DIR = Path(os.getenv("TESTFLOW_DATA_DIR", BASE_DIR / "data")).resolve()
ARTIFACT_ROOT = DATA_DIR / "artifacts"
DATABASE_URL = os.getenv("TESTFLOW_DATABASE_URL", f"sqlite:///{DATA_DIR / 'testflow.db'}")
DEFAULT_TIMEOUT_SECONDS = int(os.getenv("TESTFLOW_DEFAULT_TIMEOUT", "900"))

DATA_DIR.mkdir(parents=True, exist_ok=True)
ARTIFACT_ROOT.mkdir(parents=True, exist_ok=True)

