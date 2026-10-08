"""Read-only checks before consuming test execution resources."""

import os
from pathlib import Path
import shutil
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


def inspect_environment(project, suite, environment, devices=()):
    checks = []

    def add(name, ready, detail):
        checks.append({"name": name, "ready": bool(ready), "detail": detail})

    root = Path(project.work_dir).resolve()
    path = (root / suite.test_path).resolve()
    add("测试路径", path.exists() and (path == root or root in path.parents), suite.test_path)
    if environment.base_url:
        try:
            with urlopen(Request(environment.base_url, method="GET"), timeout=3) as response:
                add("业务服务", response.status < 500, f"HTTP {response.status}")
        except HTTPError as exc:
            add("业务服务", exc.code < 500, f"HTTP {exc.code}（服务可达，不代表业务验证通过）")
        except (URLError, OSError, ValueError):
            add("业务服务", False, "服务不可达，请检查地址及启动状态")
    if project.code == "seckill-mall" and suite.test_path == "tests/api":
        import json
        variables = {**os.environ, **json.loads(environment.variables_json or "{}")}
        names = ["API_TEST_USERNAME", "API_TEST_PASSWORD", "API_TEST_ADMIN_USERNAME", "API_TEST_ADMIN_PASSWORD"]
        if variables.get("API_TEST_DATA_VALIDATION", "").lower() in {"true", "1", "yes"}:
            names.append("API_TEST_MYSQL_PASSWORD")
        missing = [name for name in names if not variables.get(name)]
        add("测试凭据", not missing, "已配置" if not missing else "缺少: " + ", ".join(missing))
    if suite.device_required:
        add("ADB", shutil.which("adb") is not None, "ADB 可用" if shutil.which("adb") else "未找到 ADB")
        add("Android 设备", any(device.status == "ONLINE" and not device.locked_by_run_id for device in devices), "需要在线且空闲设备；请先扫描设备")
        from .appium_manager import appium_is_ready, resolve_appium_executable
        appium_ready = appium_is_ready(os.getenv("TESTFLOW_APPIUM_URL", "http://127.0.0.1:4723"))
        if not appium_ready:
            try:
                resolve_appium_executable()
                appium_ready = True
            except RuntimeError:
                pass
        add("Appium", appium_ready, "可连接或启动" if appium_ready else "Appium 未运行且未找到可执行文件")
    return {"ready": all(check["ready"] for check in checks), "checks": checks}
