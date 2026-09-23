"""用户登录和认证接口的基础自动化测试。"""

import pytest
import requests

def test_login_success(base_url, user_credentials):
    response = requests.post(
        f"{base_url}/user/login",
        json=user_credentials,
        timeout=5,
    )

    assert response.status_code == 200
    body = response.json()
    assert body["code"] == 200
    assert body["data"]["token"]
    assert body["data"]["user"]["username"] == user_credentials["username"]
    assert body["data"]["user"]["role"] == 0


def test_login_rejects_wrong_password(base_url, user_credentials):
    response = requests.post(
        f"{base_url}/user/login",
        json={"username": user_credentials["username"], "password": "wrong123456"},
        timeout=5,
    )

    assert response.status_code == 200
    body = response.json()
    assert body["code"] == 1006
    assert body["data"] is None


@pytest.mark.parametrize(
    "missing_field, expected_message",
    [
        ("username", "用户名不能为空"),
        ("password", "密码不能为空"),
    ],
    ids=["missing_username", "missing_password"],
)
def test_login_requires_field(
    base_url, user_credentials, missing_field, expected_message
):
    login_data = user_credentials.copy()
    login_data.pop(missing_field)

    response = requests.post(
        f"{base_url}/user/login",
        json=login_data,
        timeout=5,
    )

    assert response.status_code == 400
    body = response.json()
    assert body["code"] == 2001
    assert body["message"] == expected_message


def test_user_info_requires_token(base_url):
    response = requests.get(
        f"{base_url}/user/info",
        timeout=5,
    )

    assert response.status_code == 200
    body = response.json()
    assert body["code"] == 1001
    assert body["data"] is None


def test_user_info_rejects_invalid_token(base_url):
    response = requests.get(
        f"{base_url}/user/info",
        headers={"Authorization": "Bearer invalid-token"},
        timeout=5,
    )

    assert response.status_code == 200
    body = response.json()
    assert body["code"] == 1003
    assert body["message"] == "Token无效"
    assert body["data"] is None


def test_user_info_success(base_url, user_session, user_credentials):
    response = user_session.get(
        f"{base_url}/user/info",
        timeout=5,
    )

    assert response.status_code == 200
    body = response.json()
    assert body["code"] == 200
    assert body["data"]["username"] == user_credentials["username"]
    assert "password" not in body["data"]
