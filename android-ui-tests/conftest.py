from __future__ import annotations

import re
from datetime import datetime
from pathlib import Path

import pytest
from appium import webdriver
from appium.options.android import UiAutomator2Options

from config import ROOT, settings
from pages.home_page import HomePage
from pages.login_page import LoginPage
from utils.artifacts import capture_logcat
from utils.api_cleanup import TestDataCleaner


ARTIFACT_ROOT = ROOT / "artifacts"


def pytest_collection_modifyitems(config, items):
    if settings.run_mutating:
        return
    skip = pytest.mark.skip(reason="set RUN_MUTATING=true to execute data-changing UI tests")
    for item in items:
        if "mutating" in item.keywords:
            item.add_marker(skip)


@pytest.fixture(scope="session")
def ui_settings():
    return settings


@pytest.fixture()
def driver(request):
    if not settings.app_path.exists():
        pytest.fail(f"APK not found: {settings.app_path}. Run android-app/gradlew assembleDebug first.")

    options = UiAutomator2Options()
    options.platform_name = "Android"
    options.automation_name = "UiAutomator2"
    options.device_name = settings.device_name
    options.app = str(settings.app_path)
    options.app_package = settings.app_package
    options.app_activity = settings.app_activity
    options.no_reset = False
    options.full_reset = False
    options.new_command_timeout = 120
    if settings.platform_version:
        options.platform_version = settings.platform_version
    if settings.udid:
        options.udid = settings.udid

    mobile = webdriver.Remote(settings.appium_server_url, options=options)
    yield mobile

    if getattr(request.node, "rep_call", None) and request.node.rep_call.failed:
        _save_failure_artifacts(mobile, request.node.nodeid)
    mobile.quit()


@pytest.fixture()
def authenticated_home(driver, ui_settings):
    login = LoginPage(driver, ui_settings.wait_seconds)
    if login.is_open():
        login.login(ui_settings.username, ui_settings.password)
    home = HomePage(driver, ui_settings.wait_seconds)
    assert home.is_open(), "login did not reach the home screen"
    return home


@pytest.fixture()
def order_cleanup_guard(ui_settings):
    cleaner = TestDataCleaner(
        ui_settings.api_base_url,
        ui_settings.username,
        ui_settings.password,
    )
    previous = cleaner.order_numbers()
    yield
    cleaner.cancel_new_orders(previous)


@pytest.hookimpl(hookwrapper=True)
def pytest_runtest_makereport(item, call):
    outcome = yield
    report = outcome.get_result()
    setattr(item, f"rep_{report.when}", report)


def _save_failure_artifacts(driver, nodeid: str) -> None:
    safe_name = re.sub(r"[^A-Za-z0-9_.-]+", "_", nodeid)
    folder = ARTIFACT_ROOT / f"{datetime.now():%Y%m%d_%H%M%S}_{safe_name}"
    folder.mkdir(parents=True, exist_ok=True)
    screenshot = folder / "failure.png"
    source = folder / "page.xml"
    driver.save_screenshot(str(screenshot))
    source.write_text(driver.page_source, encoding="utf-8")
    capture_logcat(folder / "logcat.txt", settings.udid)
    try:
        import allure

        allure.attach.file(str(screenshot), name="failure screenshot", attachment_type=allure.attachment_type.PNG)
        allure.attach.file(str(source), name="page source", attachment_type=allure.attachment_type.XML)
        logcat = folder / "logcat.txt"
        if logcat.exists():
            allure.attach.file(str(logcat), name="logcat", attachment_type=allure.attachment_type.TEXT)
    except ImportError:
        pass
