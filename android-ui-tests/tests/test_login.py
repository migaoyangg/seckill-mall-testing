import pytest

from pages.home_page import HomePage
from pages.login_page import LoginPage


@pytest.mark.smoke
def test_empty_login_shows_validation_message(driver, ui_settings):
    login = LoginPage(driver, ui_settings.wait_seconds)
    assert login.is_open()
    login.submit_empty()
    assert login.message() == "请输入用户名和密码"


@pytest.mark.regression
def test_username_is_required(driver, ui_settings):
    login = LoginPage(driver, ui_settings.wait_seconds)
    login.login("", "any-password")
    assert login.message() == "请输入用户名和密码"


@pytest.mark.regression
def test_password_is_required(driver, ui_settings):
    login = LoginPage(driver, ui_settings.wait_seconds)
    login.login(ui_settings.username, "")
    assert login.message() == "请输入用户名和密码"


@pytest.mark.regression
def test_wrong_password_is_rejected(driver, ui_settings):
    login = LoginPage(driver, ui_settings.wait_seconds)
    login.login(ui_settings.username, "wrong-password")
    message = login.message()
    assert "错误" in message or "失败" in message


@pytest.mark.smoke
def test_valid_user_can_login_and_logout(driver, ui_settings):
    login = LoginPage(driver, ui_settings.wait_seconds)
    login.login(ui_settings.username, ui_settings.password)
    home = HomePage(driver, ui_settings.wait_seconds)
    assert home.is_open()
    home.logout()
    assert LoginPage(driver, ui_settings.wait_seconds).is_open()


@pytest.mark.regression
def test_login_session_survives_app_restart(driver, ui_settings):
    LoginPage(driver, ui_settings.wait_seconds).login(ui_settings.username, ui_settings.password)
    assert HomePage(driver, ui_settings.wait_seconds).is_open()
    driver.terminate_app(ui_settings.app_package)
    driver.activate_app(ui_settings.app_package)
    assert HomePage(driver, ui_settings.wait_seconds).is_open()


@pytest.mark.regression
def test_logout_state_survives_app_restart(driver, ui_settings):
    LoginPage(driver, ui_settings.wait_seconds).login(ui_settings.username, ui_settings.password)
    home = HomePage(driver, ui_settings.wait_seconds)
    assert home.is_open()
    home.logout()
    driver.terminate_app(ui_settings.app_package)
    driver.activate_app(ui_settings.app_package)
    assert LoginPage(driver, ui_settings.wait_seconds).is_open()
