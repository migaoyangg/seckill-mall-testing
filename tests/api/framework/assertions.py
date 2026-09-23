"""接口响应公共断言。"""

from __future__ import annotations

from typing import Any

import requests


def response_json(response: requests.Response) -> dict[str, Any]:
    """解析统一响应，并在失败时输出便于定位的信息。"""
    try:
        body = response.json()
    except ValueError as exc:
        preview = response.text[:500]
        raise AssertionError(
            f"响应不是合法 JSON: status={response.status_code}, body={preview!r}"
        ) from exc
    assert isinstance(body, dict), f"响应 JSON 顶层应为对象，实际为 {type(body).__name__}"
    for field in ("code", "message", "data"):
        assert field in body, f"统一响应缺少字段 {field}: {body}"
    return body


def assert_api_response(
    response: requests.Response,
    *,
    http_status: int = 200,
    code: int = 200,
    message_contains: str | None = None,
) -> dict[str, Any]:
    """同时校验 HTTP 状态码、业务码和可选消息。"""
    assert response.status_code == http_status, (
        f"HTTP 状态码不匹配: expected={http_status}, actual={response.status_code}, "
        f"body={response.text[:500]!r}"
    )
    body = response_json(response)
    assert body["code"] == code, f"业务码不匹配: expected={code}, body={body}"
    if message_contains is not None:
        assert message_contains in body["message"], (
            f"响应消息不包含 {message_contains!r}: {body['message']!r}"
        )
    return body
