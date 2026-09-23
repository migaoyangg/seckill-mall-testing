from appium.webdriver.common.appiumby import AppiumBy

from pages.base_page import BasePage


class ProductDialog(BasePage):
    TITLE = (AppiumBy.ID, "com.seckill.mall.android:id/alertTitle")
    MESSAGE = (AppiumBy.ID, "android:id/message")
    CLOSE = (AppiumBy.ID, "android:id/button2")
    CREATE_ORDER = (AppiumBy.ID, "android:id/button1")

    def title(self) -> str:
        return self.text(self.TITLE)

    def details(self) -> str:
        return self.text(self.MESSAGE)

    def close(self) -> None:
        self.click(self.CLOSE)

    def is_open(self) -> bool:
        return self.exists(self.MESSAGE, timeout=5)

    def create_order(self) -> None:
        self.click(self.CREATE_ORDER)
