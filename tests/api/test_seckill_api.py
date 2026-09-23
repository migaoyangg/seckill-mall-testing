"""秒杀核心链路接口测试。"""

import time

import pytest

from tests.api.framework.assertions import assert_api_response


pytestmark = [
    pytest.mark.regression,
    pytest.mark.seckill,
    pytest.mark.destructive,
    pytest.mark.data_validation,
]


def test_seckill_rejects_activity_that_has_not_started(
    user_session, seckill_activity_factory
):
    activity = seckill_activity_factory(state="future")

    assert_api_response(
        user_session.post(f"/seckill/do/{activity['seckill_goods_id']}"),
        code=4001,
        message_contains="未开始",
    )


def test_seckill_rejects_activity_that_has_ended(
    user_session, seckill_activity_factory
):
    activity = seckill_activity_factory(state="ended")

    assert_api_response(
        user_session.post(f"/seckill/do/{activity['seckill_goods_id']}"),
        code=4002,
        message_contains="已结束",
    )


def test_seckill_success_can_be_polled_and_duplicate_is_rejected(
    user_session, seckill_activity_factory, mysql_probe, redis_probe
):
    activity = seckill_activity_factory(state="active", stock=2, limit_per_user=1)
    activity_id = activity["seckill_goods_id"]

    accepted = assert_api_response(user_session.post(f"/seckill/do/{activity_id}"))["data"]
    assert accepted["status"] == "PROCESSING"
    assert accepted["remainStock"] == 1
    activity["request_id"] = accepted["requestId"]
    activity["order_no"] = accepted["orderNo"]

    assert_api_response(
        user_session.post(f"/seckill/do/{activity_id}"),
        code=4004,
        message_contains="重复",
    )

    result = accepted
    for _ in range(30):
        result = assert_api_response(
            user_session.get(f"/seckill/result/{accepted['requestId']}")
        )["data"]
        if result["status"] != "PROCESSING":
            break
        time.sleep(0.1)

    assert result["status"] == "SUCCESS", result
    order = mysql_probe.order(accepted["orderNo"])
    assert order is not None
    assert order["is_seckill"] == 1
    assert order["seckill_goods_id"] == activity_id
    assert redis_probe.get(f"seckill:stock:{activity_id}") == "1"
