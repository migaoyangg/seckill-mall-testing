import json
from types import SimpleNamespace

import pytest

from tests.api.framework.evidence import BusinessEvidence, save_evidence, wait_until
from tests.api.conftest import isolated_order_data


def test_failed_stock_assertion_is_preserved_for_platform(tmp_path):
    entries = []
    recorder = BusinessEvidence("test_stock", entries)
    with pytest.raises(AssertionError, match="库存"):
        recorder.equal("库存", 250, 0)
    target = tmp_path / "business-evidence.json"
    save_evidence(target, entries)
    saved = json.loads(target.read_text())
    assert saved["checks"][0]["status"] == "FAILED"
    assert saved["checks"][0]["actual"] == 250


def test_async_persistence_polling_and_timeout():
    values = iter([None, None, {"status": 0}])
    assert wait_until(lambda: next(values), lambda row: row is not None, interval=0) == {"status": 0}
    with pytest.raises(AssertionError, match="等待超时"):
        wait_until(lambda: None, lambda row: row is not None, timeout=0, interval=0)


@pytest.mark.parametrize("cleanup_fails", [False, True])
def test_isolated_order_cleanup_records_failure_and_reclaims_only_owned_product(cleanup_fails):
    calls = []

    def response(data=None, code=200):
        return SimpleNamespace(status_code=200, text="", json=lambda: {"code": code, "message": "ok", "data": data})

    class Client:
        def post(self, path, **kwargs):
            calls.append(path)
            return response("OWN-ORDER" if path == "/order/create" else None)

        def get(self, path):
            return response({"status": 0})

        def delete(self, path):
            calls.append(path)
            return response(code=500 if cleanup_fails else 200)

    probe = SimpleNamespace(product_by_name=lambda _: {"id": 1234, "stock": 5})
    entries = []
    factory = isolated_order_data.__wrapped__(Client(), Client(), probe, BusinessEvidence("test_order", entries))
    context = next(factory)
    context["create_order"]()
    with pytest.raises(AssertionError if cleanup_fails else StopIteration):
        next(factory)
    assert "/order/cancel/OWN-ORDER" in calls
    assert "/admin/product/delete/1234" in calls
    assert entries[-1]["status"] == ("FAILED" if cleanup_fails else "PASSED")
