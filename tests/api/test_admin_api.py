"""管理员权限接口的基础自动化测试。"""

import requests


ADMIN_PRODUCT_LIST = "/admin/product/list"


def test_admin_product_list_requires_login(base_url):
    response = requests.get(
        f"{base_url}{ADMIN_PRODUCT_LIST}",
        params={"page": 1, "size": 10},
        timeout=5,
    )

    assert response.status_code == 200
    body = response.json()
    assert body["code"] == 1001
    assert body["data"] is None


def test_admin_product_list_rejects_normal_user(base_url, user_token):
    response = requests.get(
        f"{base_url}{ADMIN_PRODUCT_LIST}",
        params={"page": 1, "size": 10},
        headers={"Authorization": f"Bearer {user_token}"},
        timeout=5,
    )

    assert response.status_code == 200
    body = response.json()
    assert body["code"] == 1002
    assert body["data"] is None


def test_admin_product_list_success(base_url, admin_session):
    response = admin_session.get(
        f"{base_url}{ADMIN_PRODUCT_LIST}",
        params={"page": 1, "size": 10},
        timeout=5,
    )

    assert response.status_code == 200
    body = response.json()
    assert body["code"] == 200
    assert body["data"]["page"] == 1
    assert body["data"]["size"] == 10
    assert isinstance(body["data"]["records"], list)
