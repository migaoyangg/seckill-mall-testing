"""接口测试环境配置。"""

from __future__ import annotations

from dataclasses import dataclass
import os


def _positive_float(name: str, default: float) -> float:
    raw_value = os.getenv(name)
    if raw_value is None:
        return default
    try:
        value = float(raw_value)
    except ValueError as exc:
        raise ValueError(f"环境变量 {name} 必须是数字，当前值为 {raw_value!r}") from exc
    if value <= 0:
        raise ValueError(f"环境变量 {name} 必须大于 0，当前值为 {raw_value!r}")
    return value


def _boolean(name: str, default: bool = False) -> bool:
    raw_value = os.getenv(name)
    if raw_value is None:
        return default
    normalized = raw_value.strip().lower()
    if normalized in {"1", "true", "yes", "on"}:
        return True
    if normalized in {"0", "false", "no", "off"}:
        return False
    raise ValueError(f"环境变量 {name} 必须是 true/false，当前值为 {raw_value!r}")


def _required(name: str) -> str:
    value = os.getenv(name)
    if value is None or not value.strip():
        raise ValueError(f"必须通过环境变量提供 {name}，请勿在仓库中保存测试凭据")
    return value


@dataclass(frozen=True)
class TestConfig:
    """从环境变量加载的接口测试配置。"""

    base_url: str
    timeout: float
    username: str
    password: str
    admin_username: str
    admin_password: str
    product_id: int
    data_validation: bool
    mysql_host: str
    mysql_port: int
    mysql_database: str
    mysql_username: str
    mysql_password: str
    redis_host: str
    redis_port: int
    redis_database: int
    redis_password: str | None

    @classmethod
    def from_env(cls) -> "TestConfig":
        base_url = os.getenv("BASE_URL", "http://127.0.0.1:8080/api").rstrip("/")
        data_validation = _boolean("API_TEST_DATA_VALIDATION")
        return cls(
            base_url=base_url,
            timeout=_positive_float("API_TEST_TIMEOUT", 5.0),
            username=_required("API_TEST_USERNAME"),
            password=_required("API_TEST_PASSWORD"),
            admin_username=_required("API_TEST_ADMIN_USERNAME"),
            admin_password=_required("API_TEST_ADMIN_PASSWORD"),
            product_id=int(os.getenv("API_TEST_PRODUCT_ID", "1")),
            data_validation=data_validation,
            mysql_host=os.getenv("API_TEST_MYSQL_HOST", "127.0.0.1"),
            mysql_port=int(os.getenv("API_TEST_MYSQL_PORT", "3306")),
            mysql_database=os.getenv("API_TEST_MYSQL_DATABASE", "seckill_mall"),
            mysql_username=os.getenv("API_TEST_MYSQL_USERNAME", "root"),
            mysql_password=(
                _required("API_TEST_MYSQL_PASSWORD") if data_validation
                else os.getenv("API_TEST_MYSQL_PASSWORD", "")
            ),
            redis_host=os.getenv("API_TEST_REDIS_HOST", "127.0.0.1"),
            redis_port=int(os.getenv("API_TEST_REDIS_PORT", "6379")),
            redis_database=int(os.getenv("API_TEST_REDIS_DATABASE", "0")),
            redis_password=os.getenv("API_TEST_REDIS_PASSWORD") or None,
        )
