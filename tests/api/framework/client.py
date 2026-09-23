"""统一 HTTP 客户端，负责基础地址、超时、日志和 Token。"""

from __future__ import annotations

import logging
from typing import Any

import requests


LOGGER = logging.getLogger(__name__)


class ApiClient:
    """面向秒购商城 API 的轻量请求客户端。"""

    def __init__(
        self,
        base_url: str,
        *,
        timeout: float = 5.0,
        token: str | None = None,
    ) -> None:
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout
        self.session = requests.Session()
        if token:
            self.set_token(token)

    def set_token(self, token: str) -> None:
        self.session.headers.update({"Authorization": f"Bearer {token}"})

    def clear_token(self) -> None:
        self.session.headers.pop("Authorization", None)

    def request(self, method: str, path: str, **kwargs: Any) -> requests.Response:
        timeout = kwargs.pop("timeout", self.timeout)
        url = path if path.startswith(("http://", "https://")) else f"{self.base_url}/{path.lstrip('/')}"
        LOGGER.info("API request method=%s path=%s", method.upper(), path)
        response = self.session.request(method, url, timeout=timeout, **kwargs)
        LOGGER.info(
            "API response method=%s path=%s status=%s elapsed_ms=%.1f",
            method.upper(),
            path,
            response.status_code,
            response.elapsed.total_seconds() * 1000,
        )
        return response

    def get(self, path: str, **kwargs: Any) -> requests.Response:
        return self.request("GET", path, **kwargs)

    def post(self, path: str, **kwargs: Any) -> requests.Response:
        return self.request("POST", path, **kwargs)

    def put(self, path: str, **kwargs: Any) -> requests.Response:
        return self.request("PUT", path, **kwargs)

    def delete(self, path: str, **kwargs: Any) -> requests.Response:
        return self.request("DELETE", path, **kwargs)

    def close(self) -> None:
        self.session.close()

    def __enter__(self) -> "ApiClient":
        return self

    def __exit__(self, *_args: object) -> None:
        self.close()
