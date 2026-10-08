"""Human-confirmed defect submission to a configured ZenTao REST API v1."""

from __future__ import annotations

import json
import os
from html import escape
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener


class DefectProviderError(Exception):
    pass


class _NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        return None


def zentao_configured() -> bool:
    base = os.getenv("TESTFLOW_ZENTAO_URL", "").rstrip("/")
    product_id = os.getenv("TESTFLOW_ZENTAO_PRODUCT_ID", "")
    parsed = urlsplit(base)
    return bool(
        os.getenv("TESTFLOW_ZENTAO_TOKEN")
        and parsed.scheme in {"http", "https"}
        and parsed.netloc
        and product_id.isdecimal()
        and int(product_id) > 0
    )


def create_zentao_bug(*, title: str, steps: str, severity: int, priority: int) -> tuple[str, str]:
    base = os.getenv("TESTFLOW_ZENTAO_URL", "").rstrip("/")
    token = os.getenv("TESTFLOW_ZENTAO_TOKEN", "")
    product_id = os.getenv("TESTFLOW_ZENTAO_PRODUCT_ID", "")
    if not zentao_configured():
        raise DefectProviderError("ZenTao URL, token and numeric product ID must be configured")

    url = f"{base}/api.php/v1/products/{product_id}/bugs"
    body = json.dumps({
        "title": title, "steps": escape(steps).replace("\n", "<br>"), "severity": severity, "pri": priority,
        "type": "codeerror", "openedBuild": ["trunk"],
    }, ensure_ascii=False).encode("utf-8")
    request = Request(url, data=body, headers={"Token": token, "Content-Type": "application/json"}, method="POST")
    try:
        with build_opener(_NoRedirect()).open(request, timeout=10) as response:
            payload = json.load(response)
    except (HTTPError, URLError, TimeoutError, OSError, ValueError) as exc:
        raise DefectProviderError("ZenTao request failed; check its URL, credentials and availability") from exc
    if not isinstance(payload, dict) or not payload.get("id") or payload.get("status") == "fail":
        raise DefectProviderError("ZenTao did not return a successful bug ID")
    bug_id = str(payload["id"])
    if not bug_id.isdecimal():
        raise DefectProviderError("ZenTao returned an invalid bug ID")
    return bug_id, f"{base}/bug-view-{bug_id}.html"
