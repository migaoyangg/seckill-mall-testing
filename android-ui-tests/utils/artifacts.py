from __future__ import annotations

import shutil
import subprocess
from pathlib import Path


def capture_logcat(output: Path, udid: str = "") -> bool:
    adb = shutil.which("adb")
    if not adb:
        return False
    command = [adb]
    if udid:
        command.extend(["-s", udid])
    command.extend(["logcat", "-d", "-v", "threadtime"])
    result = subprocess.run(command, capture_output=True, text=True, timeout=20, check=False)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(result.stdout + result.stderr, encoding="utf-8")
    return result.returncode == 0

