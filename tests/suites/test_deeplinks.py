"""Ссылки duken://product/{id}: из push-уведомления, мессенджера, рекламы."""
import allure

from framework import device
from pages.screens import CatalogPage, LoginPage, ProductPage


@allure.feature("Ссылки")
class TestDeepLinks:
    def test_link_opens_product(self, app):
        app.start_logged_in()
        app.open_link("duken://product/3")
        assert ProductPage(app.driver).title() == app.api.product(3)["name"]

    def test_back_from_link_returns_to_previous_screen(self, app):
        catalog = app.start_logged_in()
        app.nav.orders()
        app.open_link("duken://product/3")
        ProductPage(app.driver).title()
        app.driver.back()
        assert catalog.visible("nav_orders", 5)
        assert catalog.el("nav_orders").get_attribute("selected") == "true"

    def test_link_does_not_duplicate_app(self, app):
        """Ссылка приходит в открытое приложение, а не запускает его вторую копию поверх первой."""
        app.start_logged_in()
        app.open_link("duken://product/3")
        ProductPage(app.driver).title()
        app.open_link("duken://product/7")
        ProductPage(app.driver).wait_text("product_title", app.api.product(7)["name"])
        assert device.activity_count() == 1

    def test_link_when_app_closed(self, app):
        user = app.api.register()
        app.launch_logged_in(user).wait_loaded()
        app.driver.terminate_app("kz.duken.mobile")
        app.open_link("duken://product/7")
        assert ProductPage(app.driver).title() == app.api.product(7)["name"]

    def test_link_before_login_opens_after_login(self, app):
        user = app.api.register()
        device.launch({"api_base_url": "http://10.0.2.2:8000/"}, uri="duken://product/5")
        login = LoginPage(app.driver)
        assert login.is_open()
        login.login(user["email"], user["password"])
        assert ProductPage(app.driver).title() == app.api.product(5)["name"]

    def test_link_to_missing_product(self, app):
        app.start_logged_in()
        app.open_link("duken://product/99999")
        page = ProductPage(app.driver)
        assert page.text("error_text", 10) == "Товар не найден"
        assert not page.visible("retry_button")  # 404 повтором не исправить

    def test_malformed_link_does_not_crash(self, app):
        app.start_logged_in()
        app.open_link("duken://product/abc")
        CatalogPage(app.driver).wait_loaded()
