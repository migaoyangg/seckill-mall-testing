from appium.webdriver.common.appiumby import AppiumBy

from pages.base_page import BasePage


class HomePage(BasePage):
    PRODUCTS = (AppiumBy.ID, "com.seckill.mall.android:id/productsButton")
    ORDERS = (AppiumBy.ID, "com.seckill.mall.android:id/ordersButton")
    LOGOUT = (AppiumBy.ID, "com.seckill.mall.android:id/logoutButton")
    LIST = (AppiumBy.ID, "com.seckill.mall.android:id/listView")
    EMPTY = (AppiumBy.ID, "com.seckill.mall.android:id/emptyText")
    PRODUCT_NAME = (AppiumBy.ID, "com.seckill.mall.android:id/name")
    ORDER_NO = (AppiumBy.ID, "com.seckill.mall.android:id/orderNo")

    def is_open(self) -> bool:
        return self.exists(self.LOGOUT, timeout=8)

    def open_products(self) -> None:
        self.click(self.PRODUCTS)

    def open_orders(self) -> None:
        self.click(self.ORDERS)

    def logout(self) -> None:
        self.click(self.LOGOUT)

    def wait_for_product(self):
        return self.visible(self.PRODUCT_NAME)

    def open_first_product(self) -> str:
        product = self.wait_for_product()
        name = product.text
        product.click()
        return name

    def first_order_text(self) -> str:
        return self.text(self.ORDER_NO)

    def first_product_name(self) -> str:
        return self.text(self.PRODUCT_NAME)
