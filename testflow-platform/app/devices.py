from __future__ import annotations

import os
import shutil
import subprocess
from datetime import datetime
from pathlib import Path

from sqlalchemy import update
from sqlalchemy.orm import Session

from .db import AndroidDevice, SessionLocal


def adb_path() -> str:
    configured = os.getenv("TESTFLOW_ADB_PATH")
    candidates = [
        configured,
        shutil.which("adb"),
        str(Path.home() / "Library/Android/sdk/platform-tools/adb"),
    ]
    for candidate in candidates:
        if candidate and Path(candidate).is_file() and os.access(candidate, os.X_OK):
            return candidate
    raise RuntimeError("ADB executable not found; set TESTFLOW_ADB_PATH")


def parse_adb_devices(output: str) -> list[dict[str, str]]:
    devices: list[dict[str, str]] = []
    for raw_line in output.splitlines()[1:]:
        line = raw_line.strip()
        if not line or line.startswith("*"):
            continue
        parts = line.split()
        if len(parts) < 2:
            continue
        serial, state = parts[0], parts[1]
        metadata = {}
        for part in parts[2:]:
            if ":" in part:
                key, value = part.split(":", 1)
                metadata[key] = value
        devices.append(
            {
                "serial": serial,
                "status": "ONLINE" if state == "device" else state.upper(),
                "model": metadata.get("model", ""),
                "product": metadata.get("product", ""),
            }
        )
    return devices


def scan_devices() -> list[AndroidDevice]:
    executable = adb_path()
    result = subprocess.run(
        [executable, "devices", "-l"],
        capture_output=True,
        text=True,
        timeout=10,
        check=True,
    )
    observed = parse_adb_devices(result.stdout)
    now = datetime.utcnow()
    with SessionLocal() as db:
        known = {item.serial: item for item in db.query(AndroidDevice).all()}
        observed_serials = set()
        for item in observed:
            observed_serials.add(item["serial"])
            device = known.get(item["serial"])
            if not device:
                device = AndroidDevice(serial=item["serial"])
                db.add(device)
            device.status = item["status"]
            device.model = item["model"]
            device.product = item["product"]
            device.last_seen_at = now
            if item["status"] == "ONLINE":
                try:
                    version = subprocess.run(
                        [executable, "-s", item["serial"], "shell", "getprop", "ro.build.version.release"],
                        capture_output=True,
                        text=True,
                        timeout=5,
                    )
                    device.android_version = version.stdout.strip()
                except subprocess.SubprocessError:
                    pass
        for serial, device in known.items():
            if serial not in observed_serials and device.locked_by_run_id is None:
                device.status = "OFFLINE"
        db.commit()
        return db.query(AndroidDevice).order_by(AndroidDevice.serial).all()


def acquire_device(db: Session, run_id: int) -> AndroidDevice | None:
    candidates = (
        db.query(AndroidDevice)
        .filter(AndroidDevice.status == "ONLINE", AndroidDevice.locked_by_run_id.is_(None))
        .order_by(AndroidDevice.id)
        .all()
    )
    for device in candidates:
        result = db.execute(
            update(AndroidDevice)
            .where(AndroidDevice.id == device.id, AndroidDevice.locked_by_run_id.is_(None))
            .values(locked_by_run_id=run_id)
        )
        db.commit()
        if result.rowcount == 1:
            db.refresh(device)
            return device
    return None


def release_device(run_id: int) -> None:
    with SessionLocal() as db:
        db.execute(
            update(AndroidDevice)
            .where(AndroidDevice.locked_by_run_id == run_id)
            .values(locked_by_run_id=None)
        )
        db.commit()

