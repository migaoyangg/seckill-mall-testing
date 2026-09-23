"""订单列表接口的只读自动化测试。"""

import pytest
import requests

from tests.api.framework.assertions import assert_api_response


pytestmark = pytest.mark.regression


def test_order_list_requires_login(base_url):
    response = requests.get(
        f"{base_url}/order/list",
        params={"page": 1, "size": 10},
        timeout=5,
    )

    assert response.status_code == 200
    body = response.json()
    assert body["code"] == 1001
    assert body["data"] is None


def test_order_list_success_for_logged_in_user(base_url, user_session):
    response = user_session.get(
        f"{base_url}/order/list",
        params={"page": 1, "size": 10},
        timeout=5,
    )

    assert response.status_code == 200
    body = response.json()
    assert body["code"] == 200
    assert body["data"]["page"] == 1
    assert body["data"]["size"] == 10
    assert isinstance(body["data"]["records"], list)


def test_order_create_detail_cancel_lifecycle(base_url, user_session, order_factory):
    order_no = order_factory()

    detail_response = user_session.get(
        f"{base_url}/order/detail/{order_no}",
        timeout=5,
    )
    assert detail_response.status_code == 200
    detail_body = detail_response.json()
    assert detail_body["code"] == 200
    assert detail_body["data"]["status"] == 0
    assert detail_body["data"]["statusDesc"] == "待支付"

    cancel_response = user_session.post(
        f"{base_url}/order/cancel/{order_no}",
        timeout=5,
    )
    assert cancel_response.status_code == 200
    assert cancel_response.json()["code"] == 200

    cancelled_detail_response = user_session.get(
        f"{base_url}/order/detail/{order_no}",
        timeout=5,
    )
    assert cancelled_detail_response.status_code == 200
    cancelled_body = cancelled_detail_response.json()
    assert cancelled_body["code"] == 200
    assert cancelled_body["data"]["status"] == 4
    assert cancelled_body["data"]["statusDesc"] == "已取消"

    repeat_cancel_response = user_session.post(
        f"{base_url}/order/cancel/{order_no}",
        timeout=5,
    )
    assert repeat_cancel_response.status_code == 200
    assert repeat_cancel_response.json()["code"] == 3005


def test_order_payment_is_idempotent_and_rejects_other_transaction(
    base_url, user_session, order_factory
):
    """同一支付请求可安全重试，但另一条流水不能覆盖已支付订单。"""
    order_no = order_factory()

    first_pay_response = user_session.post(
        f"{base_url}/order/pay/{order_no}",
        params={"payType": 1},
        timeout=5,
    )
    assert first_pay_response.status_code == 200
    assert first_pay_response.json()["code"] == 200

    repeated_pay_response = user_session.post(
        f"{base_url}/order/pay/{order_no}",
        params={"payType": 1},
        timeout=5,
    )
    assert repeated_pay_response.status_code == 200
    assert repeated_pay_response.json()["code"] == 200

    different_transaction_response = user_session.post(
        f"{base_url}/order/pay/callback/{order_no}",
        params={
            "payType": 1,
            "transactionNo": f"OTHER_{order_no}",
        },
        timeout=5,
    )
    assert different_transaction_response.status_code == 200
    different_transaction_body = different_transaction_response.json()
    assert different_transaction_body["code"] == 3005
    assert "其他支付流水" in different_transaction_body["message"]

    detail_response = user_session.get(
        f"{base_url}/order/detail/{order_no}",
        timeout=5,
    )
    assert detail_response.status_code == 200
    detail_body = detail_response.json()
    assert detail_body["code"] == 200
    assert detail_body["data"]["status"] == 1
    assert detail_body["data"]["statusDesc"] == "已支付"


def test_order_create_rejects_zero_quantity(base_url, user_session, test_config):
    response = user_session.post(
        f"{base_url}/order/create",
        params={"productId": test_config.product_id, "quantity": 0},
        timeout=5,
    )

    assert response.status_code == 400
    body = response.json()
    assert body["code"] == 2001
    assert "购买数量必须大于0" in body["message"]


def test_order_detail_not_found(base_url, user_session):
    response = user_session.get(
        f"{base_url}/order/detail/not-exist-order-999",
        timeout=5,
    )

    assert response.status_code == 200
    body = response.json()
    assert body["code"] == 3004
    assert body["data"] is None


@pytest.mark.destructive
def test_order_refund_rejects_unpaid_order(base_url, user_session, order_factory):
    """待支付订单不能跳过支付直接退款。"""
    order_no = order_factory()

    response = user_session.post(
        f"{base_url}/order/refund/{order_no}",
        timeout=5,
    )

    body = assert_api_response(
        response,
        code=3005,
        message_contains="仅已支付订单允许退款",
    )
    assert body["data"] is None


@pytest.mark.destructive
def test_order_refund_succeeds_and_is_idempotent(base_url, user_session, order_factory):
    """已支付订单退款成功，重复退款保持幂等。"""
    order_no = order_factory()
    assert_api_response(
        user_session.post(
            f"{base_url}/order/pay/{order_no}",
            params={"payType": 1},
            timeout=5,
        )
    )

    first_refund = user_session.post(
        f"{base_url}/order/refund/{order_no}",
        timeout=5,
    )
    assert_api_response(first_refund)

    repeated_refund = user_session.post(
        f"{base_url}/order/refund/{order_no}",
        timeout=5,
    )
    assert_api_response(repeated_refund)

    detail = user_session.get(
        f"{base_url}/order/detail/{order_no}",
        timeout=5,
    )
    detail_body = assert_api_response(detail)
    assert detail_body["data"]["status"] == 7
    assert detail_body["data"]["statusDesc"] == "已退款"


@pytest.mark.destructive
@pytest.mark.data_validation
def test_refund_restores_mysql_stock_and_persists_refund_state(
    base_url,
    test_config,
    user_session,
    order_factory,
    mysql_probe,
):
    """退款后MySQL订单状态和普通商品库存保持一致。"""
    stock_before = mysql_probe.product_stock(test_config.product_id)
    order_no = order_factory()

    stock_after_create = mysql_probe.product_stock(test_config.product_id)
    assert stock_after_create == stock_before - 1

    assert_api_response(
        user_session.post(
            f"{base_url}/order/pay/{order_no}",
            params={"payType": 1},
        )
    )
    assert_api_response(user_session.post(f"{base_url}/order/refund/{order_no}"))

    order = mysql_probe.order(order_no)
    assert order is not None
    assert order["status"] == 7
    assert order["refund_time"] is not None
    assert mysql_probe.product_stock(test_config.product_id) == stock_before


@pytest.mark.destructive
@pytest.mark.data_validation
def test_cancel_removes_order_timeout_key(
    base_url,
    user_session,
    order_factory,
    redis_probe,
):
    """普通订单创建后写入超时Key，取消后删除该Key。"""
    order_no = order_factory()
    timeout_key = f"order:timeout:{order_no}"
    assert redis_probe.exists(timeout_key)

    assert_api_response(user_session.post(f"{base_url}/order/cancel/{order_no}"))

    assert not redis_probe.exists(timeout_key)
