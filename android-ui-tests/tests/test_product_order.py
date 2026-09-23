import pytest

from pages.product_dialog import ProductDialog


@pytest.mark.smoke
def test_product_list_displays_product(authenticated_home):
    assert authenticated_home.first_product_name()


@pytest.mark.smoke
def test_product_detail_displays_price_and_stock(authenticated_home, ui_settings):
    home = authenticated_home
    product_name = home.open_first_product()
    dialog = ProductDialog(home.driver, ui_settings.wait_seconds)
    assert dialog.title() == product_name
    details = dialog.details()
    assert "价格：" in details
    assert "库存：" in details
    dialog.close()


@pytest.mark.regression
def test_closing_product_detail_returns_to_product_list(authenticated_home, ui_settings):
    home = authenticated_home
    home.open_first_product()
    dialog = ProductDialog(home.driver, ui_settings.wait_seconds)
    assert dialog.is_open()
    dialog.close()
    assert home.first_product_name()


@pytest.mark.smoke
def test_orders_tab_can_be_opened(authenticated_home):
    home = authenticated_home
    home.open_orders()
    assert home.exists(home.LIST, timeout=5)


@pytest.mark.regression
def test_switching_from_orders_back_to_products(authenticated_home):
    home = authenticated_home
    home.open_orders()
    home.open_products()
    assert home.first_product_name()


@pytest.mark.mutating
@pytest.mark.regression
def test_create_order_appears_in_order_list(order_cleanup_guard, authenticated_home, ui_settings):
    home = authenticated_home
    home.open_first_product()
    ProductDialog(home.driver, ui_settings.wait_seconds).create_order()
    home.open_orders()
    assert home.first_order_text().startswith("订单号：")
