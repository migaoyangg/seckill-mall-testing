"""商品接口的基础接口自动化测试。"""

import requests

def test_search_product_success(base_url):
    response = requests.get(
        f"{base_url}/product/search",
        params={"keyword": "phone", "page": 1, "size": 10},
        timeout=5,
    )

    assert response.status_code == 200
    body = response.json()
    assert body["code"] == 200
    assert body["data"]["page"] == 1
    assert body["data"]["size"] == 10


def test_search_product_requires_keyword(base_url):
    response = requests.get(
        f"{base_url}/product/search",
        timeout=5,
    )

    assert response.status_code == 400
    body = response.json()
    assert body["code"] == 2001
    assert body["message"] == "缺少必要参数: keyword"


def test_search_product_rejects_non_numeric_page(base_url):
    response = requests.get(
        f"{base_url}/product/search",
        params={"keyword": "phone", "page": "abc"},
        timeout=5,
    )

    assert response.status_code == 400
    body = response.json()
    assert body["code"] == 2001
    assert body["message"] == "参数类型错误: page"


def test_get_existing_product_detail(base_url):
    response = requests.get(
        f"{base_url}/product/detail/1",
        timeout=5,
    )

    assert response.status_code == 200
    body = response.json()
    assert body["code"] == 200
    assert body["data"]["id"] == 1


def test_get_missing_product_detail(base_url):
    response = requests.get(
        f"{base_url}/product/detail/999",
        timeout=5,
    )

    assert response.status_code == 200
    body = response.json()
    assert body["code"] == 3002
    assert body["data"] is None
