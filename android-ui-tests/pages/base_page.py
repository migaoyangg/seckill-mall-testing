from __future__ import annotations

from pathlib import Path

from appium.webdriver.webdriver import WebDriver
from selenium.common.exceptions import TimeoutException
from selenium.webdriver.support import expected_conditions as ec
from selenium.webdriver.support.ui import WebDriverWait


class BasePage:
    def __init__(self, driver: WebDriver, timeout: int = 12) -> None:
        self.driver = driver
        self.wait = WebDriverWait(driver, timeout)

    def visible(self, locator):
        return self.wait.until(ec.visibility_of_element_located(locator))

    def clickable(self, locator):
        return self.wait.until(ec.element_to_be_clickable(locator))

    def click(self, locator) -> None:
        self.clickable(locator).click()

    def type(self, locator, value: str, clear: bool = True) -> None:
        element = self.visible(locator)
        if clear:
            element.clear()
        element.send_keys(value)

    def text(self, locator) -> str:
        return self.visible(locator).text

    def non_empty_text(self, locator) -> str:
        def read_text(driver):
            value = driver.find_element(*locator).text.strip()
            return value or False

        return self.wait.until(read_text)

    def exists(self, locator, timeout: float = 2) -> bool:
        try:
            WebDriverWait(self.driver, timeout).until(ec.presence_of_element_located(locator))
            return True
        except TimeoutException:
            return False

    def save_screenshot(self, path: Path) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        self.driver.save_screenshot(str(path))
