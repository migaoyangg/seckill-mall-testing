from appium.webdriver.common.appiumby import AppiumBy

from pages.base_page import BasePage


class LoginPage(BasePage):
    USERNAME = (AppiumBy.ID, "com.seckill.mall.android:id/usernameInput")
    PASSWORD = (AppiumBy.ID, "com.seckill.mall.android:id/passwordInput")
    LOGIN = (AppiumBy.ID, "com.seckill.mall.android:id/loginButton")
    MESSAGE = (AppiumBy.ID, "com.seckill.mall.android:id/loginMessage")

    def is_open(self) -> bool:
        return self.exists(self.LOGIN, timeout=5)

    def login(self, username: str, password: str) -> None:
        self.type(self.USERNAME, username)
        self.type(self.PASSWORD, password)
        self.click(self.LOGIN)

    def submit_empty(self) -> None:
        self.click(self.LOGIN)

    def message(self) -> str:
        return self.non_empty_text(self.MESSAGE)
