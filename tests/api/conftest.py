"""接口自动化测试的公共 pytest fixture。"""

import logging
from datetime import datetime, timedelta
from uuid import uuid4

import pytest

from tests.api.framework.assertions import assert_api_response
from tests.api.framework.client import ApiClient
from tests.api.framework.config import TestConfig
from tests.api.framework.data_probes import MySqlProbe, RedisProbe


LOGGER = logging.getLogger(__name__)


@pytest.fixture(scope="session")
def test_config():
    """加载一次测试环境配置。"""
    return TestConfig.from_env()


@pytest.fixture(scope="session")
def base_url(test_config):
    """返回被测服务的基础地址。"""
    return test_config.base_url


@pytest.fixture(scope="session")
def user_credentials(test_config):
    """返回接口测试使用的普通用户账号。"""
    return {
        "username": test_config.username,
        "password": test_config.password,
    }


@pytest.fixture(scope="session")
def admin_credentials(test_config):
    """返回接口测试使用的管理员账号。"""
    return {
        "username": test_config.admin_username,
        "password": test_config.admin_password,
    }


@pytest.fixture
def api_client(test_config):
    """返回不带认证信息的统一 HTTP 客户端。"""
    with ApiClient(test_config.base_url, timeout=test_config.timeout) as client:
        yield client


@pytest.fixture
def mysql_probe(test_config):
    """按需提供MySQL只读校验；未显式开启时跳过相关用例。"""
    if not test_config.data_validation:
        pytest.skip("设置 API_TEST_DATA_VALIDATION=true 后执行MySQL一致性校验")
    probe = MySqlProbe(test_config)
    yield probe
    probe.close()


@pytest.fixture
def redis_probe(test_config):
    """按需提供Redis只读校验；未显式开启时跳过相关用例。"""
    if not test_config.data_validation:
        pytest.skip("设置 API_TEST_DATA_VALIDATION=true 后执行Redis一致性校验")
    probe = RedisProbe(test_config)
    yield probe
    probe.close()


@pytest.fixture
def user_token(api_client, user_credentials):
    """登录一次，向需要认证的测试提供有效 Token。"""
    response = api_client.post("/user/login", json=user_credentials)
    body = assert_api_response(response)
    assert body["data"]["token"]
    return body["data"]["token"]


@pytest.fixture
def admin_token(api_client, admin_credentials):
    """登录一次，向需要管理员权限的测试提供有效 Token。"""
    response = api_client.post("/user/login", json=admin_credentials)
    body = assert_api_response(response)
    assert body["data"]["user"]["role"] == 1
    assert body["data"]["token"]
    return body["data"]["token"]


@pytest.fixture
def user_session(test_config, user_token):
    """返回已经携带普通用户 Token 的统一 HTTP 客户端。"""
    with ApiClient(
        test_config.base_url,
        timeout=test_config.timeout,
        token=user_token,
    ) as client:
        yield client


@pytest.fixture
def admin_session(test_config, admin_token):
    """返回已经携带管理员 Token 的统一 HTTP 客户端。"""
    with ApiClient(
        test_config.base_url,
        timeout=test_config.timeout,
        token=admin_token,
    ) as client:
        yield client


@pytest.fixture
def order_factory(base_url, test_config, user_session):
    """创建并登记普通订单，用例结束后通过业务接口恢复库存。"""
    created_order_numbers = []

    def create(*, product_id=None, quantity=1):
        response = user_session.post(
            f"{base_url}/order/create",
            params={
                "productId": product_id or test_config.product_id,
                "quantity": quantity,
            },
        )
        body = assert_api_response(response)
        order_no = body["data"]
        assert isinstance(order_no, str) and order_no
        created_order_numbers.append(order_no)
        return order_no

    yield create

    for order_no in reversed(created_order_numbers):
        try:
            detail = assert_api_response(
                user_session.get(f"{base_url}/order/detail/{order_no}")
            )["data"]
            status = detail["status"]
            if status == 0:
                assert_api_response(
                    user_session.post(f"{base_url}/order/cancel/{order_no}")
                )
                LOGGER.info("测试订单已取消并恢复库存: orderNo=%s", order_no)
            elif status == 1:
                assert_api_response(
                    user_session.post(f"{base_url}/order/refund/{order_no}")
                )
                LOGGER.info("测试订单已退款并恢复库存: orderNo=%s", order_no)
        except Exception:
            LOGGER.exception("测试订单清理失败，请人工检查: orderNo=%s", order_no)


@pytest.fixture
def seckill_activity_factory(admin_session, user_session, mysql_probe, redis_probe):
    """动态创建隔离的商品和秒杀活动，并在用例结束后回收数据。"""
    resources = []

    def create(*, state="active", stock=2, limit_per_user=1):
        now = datetime.now().replace(microsecond=0)
        windows = {
            "future": (now + timedelta(minutes=10), now + timedelta(minutes=20)),
            "active": (now - timedelta(minutes=2), now + timedelta(minutes=10)),
            "ended": (now - timedelta(minutes=20), now - timedelta(minutes=10)),
        }
        if state not in windows:
            raise ValueError(f"不支持的秒杀状态: {state}")

        product_name = f"API秒杀测试商品-{uuid4().hex[:10]}"
        assert_api_response(admin_session.post("/admin/product/add", json={
            "name": product_name,
            "description": "pytest动态测试数据",
            "price": 100,
            "stock": stock,
            "categoryId": 2,
            "brand": "API-TEST",
            "mainImage": "",
            "detailImages": "[]",
        }))
        product = mysql_probe.product_by_name(product_name)
        assert product is not None, f"未找到动态创建的商品: {product_name}"

        start_time, end_time = windows[state]
        assert_api_response(admin_session.post("/admin/seckill/create", json={
            "productId": product["id"],
            "seckillPrice": 50,
            "stockCount": stock,
            "startTime": start_time.isoformat(),
            "endTime": end_time.isoformat(),
            "limitPerUser": limit_per_user,
        }))
        seckill = mysql_probe.seckill_by_product_id(product["id"])
        assert seckill is not None, f"未找到动态创建的秒杀活动: productId={product['id']}"

        resource = {
            "product_id": int(product["id"]),
            "seckill_goods_id": int(seckill["id"]),
            "stock": stock,
            "order_no": None,
            "request_id": None,
        }
        resources.append(resource)
        if state == "active":
            assert_api_response(admin_session.put(
                f"/admin/seckill/status/{resource['seckill_goods_id']}/1"
            ))
        return resource

    yield create

    user_info = assert_api_response(user_session.get("/user/info"))["data"]
    user_id = user_info["id"]
    for resource in reversed(resources):
        order_no = resource["order_no"]
        if order_no:
            try:
                detail = assert_api_response(user_session.get(f"/order/detail/{order_no}"))["data"]
                if detail["status"] == 0:
                    assert_api_response(user_session.post(f"/order/cancel/{order_no}"))
            except Exception:
                LOGGER.exception("秒杀测试订单清理失败: orderNo=%s", order_no)

        activity_id = resource["seckill_goods_id"]
        request_id = resource["request_id"]
        exact_keys = [
            f"seckill:limit:{activity_id}:{user_id}",
            f"seckill:repeat:{activity_id}:{user_id}",
            f"seckill:rate:{activity_id}:{user_id}",
        ]
        if request_id:
            exact_keys.extend([
                f"seckill:result:{request_id}",
                f"seckill:result:user:{request_id}",
            ])
        redis_probe.delete_exact(*exact_keys)
        try:
            assert_api_response(admin_session.put(f"/admin/seckill/status/{activity_id}/2"))
            assert_api_response(admin_session.delete(f"/admin/seckill/delete/{activity_id}"))
            assert_api_response(admin_session.delete(
                f"/admin/product/delete/{resource['product_id']}"
            ))
        except Exception:
            LOGGER.exception("秒杀动态测试数据清理失败: activityId=%s", activity_id)
