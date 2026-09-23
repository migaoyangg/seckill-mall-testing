from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parent


def _bool_env(name: str, default: bool = False) -> bool:
    return os.getenv(name, str(default)).strip().lower() in {"1", "true", "yes", "on"}


def _required_env(name: str) -> str:
    value = os.getenv(name)
    if value is None or not value.strip():
        raise ValueError(f"{name} is required; do not store test credentials in the repository")
    return value


@dataclass(frozen=True)
class Settings:
    appium_server_url: str = os.getenv("APPIUM_SERVER_URL", "http://127.0.0.1:4723")
    device_name: str = os.getenv("ANDROID_DEVICE_NAME", "Android Emulator")
    platform_version: str = os.getenv("ANDROID_PLATFORM_VERSION", "")
    udid: str = os.getenv("ANDROID_UDID", "")
    app_path: Path = Path(
        os.getenv(
            "ANDROID_APP_PATH",
            ROOT.parent / "android-app/app/build/outputs/apk/debug/app-debug.apk",
        )
    ).expanduser().resolve()
    app_package: str = os.getenv("ANDROID_APP_PACKAGE", "com.seckill.mall.android")
    app_activity: str = os.getenv("ANDROID_APP_ACTIVITY", ".MainActivity")
    api_base_url: str = os.getenv("TEST_API_BASE_URL", "http://127.0.0.1:8080/api")
    username: str = _required_env("TEST_USERNAME")
    password: str = _required_env("TEST_PASSWORD")
    run_mutating: bool = _bool_env("RUN_MUTATING")
    wait_seconds: int = int(os.getenv("UI_WAIT_SECONDS", "12"))


settings = Settings()
