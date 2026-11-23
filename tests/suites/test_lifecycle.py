"""Особенности мобильной платформы: экран поворачивается, приложение уходит в фон,
система убивает процесс ради памяти. Пользователь не должен терять введённое и выбранное."""
import allure
import pytest

from framework import device
from pages.screens import CatalogPage, LoginPage, PaymentPage, SuccessPage
from suites.test_checkout import cart_with


def rotate(app):
    app.driver.orientation = "LANDSCAPE"
    app.driver.orientation = "PORTRAIT"


@allure.feature("Жизненный цикл")
@pytest.mark.lifecycle
class TestRotation:
    def test_login_form_survives_rotation(self, app):
        login = app.start()
        login.type("email_input", "rotate@test.kz")
        login.type("password_input", "Secret-123")
        rotate(app)
        assert login.text("email_input") == "rotate@test.kz"
        # Пароль виден как точки, но длина сохраняется
        assert len(login.text("password_input")) == len("Secret-123")

    def test_catalog_filters_survive_rotation(self, app):
        catalog = app.start_logged_in()
        catalog.choose_category("home")
        catalog.search("xiaomi")
        expected = sorted(p["name"] for p in app.api.products(category="home", q="xiaomi")["items"])
        catalog.wait(10).until(lambda _: sorted(catalog.product_names()) == expected)
        before = catalog.product_names()
        rotate(app)
        assert catalog.text("search_input") == "xiaomi"
        assert catalog.scroll_right_to("category_chips", "category_home").get_attribute("checked") == "true"
        catalog.wait(10).until(lambda _: catalog.product_names() == before)

    def test_applied_promo_survives_rotation(self, app):
        cart = cart_with(app, 1)
        cart.apply_promo("WELCOME10")
        discount = cart.text("discount", 10)
        rotate(app)
        cart.wait_quote()
        assert cart.discount() == discount, "промокод пропал после поворота экрана"

    def test_landscape_layout_has_checkout_button(self, app):
        """В альбомной ориентации кнопка оформления не уезжает за экран."""
        cart = cart_with(app, 1, 2, 3)
        app.driver.orientation = "LANDSCAPE"
        cart.wait_quote()
        button = cart.el("checkout_button")
        size = app.driver.get_window_size()
        r = button.rect
        assert r["y"] + r["height"] <= size["height"], "кнопка за пределами экрана"
        cart.checkout().wait_open()


@allure.feature("Жизненный цикл")
@pytest.mark.lifecycle
class TestBackground:
    def test_background_and_return_keeps_screen(self, app):
        catalog = app.start_logged_in()
        product = catalog.open_product(5)
        title = product.title()
        device.press_home()
        device.bring_to_front()
        assert product.title() == title

    def test_process_death_on_catalog_restores_search(self, app):
        catalog = app.start_logged_in()
        catalog.search("чай")
        catalog.wait(10).until(lambda _: len(catalog.product_names()) >= 1)
        old_pid = device.pid()
        device.kill_in_background()
        device.bring_to_front()
        catalog = CatalogPage(app.driver).wait_loaded()
        assert device.pid() != old_pid, "процесс не перезапускался — проверка ничего не доказала"
        assert catalog.text("search_input") == "чай"

    def test_process_death_on_payment_screen(self, app):
        """Пользователь ушёл из оплаты в банковское приложение, система убила процесс.
        По возвращении — тот же заказ и та же форма оплаты, без падения."""
        payment = cart_with(app, 2).checkout().wait_open()
        order_id = payment.order_id()
        device.kill_in_background()
        device.bring_to_front()
        payment = PaymentPage(app.driver).wait_open()
        assert payment.order_id() == order_id
        payment.pay()
        assert SuccessPage(app.driver).is_open()

    def test_process_death_on_login_keeps_email(self, app):
        login = app.start()
        login.type("email_input", "restore@test.kz")
        device.kill_in_background()
        device.bring_to_front()
        login = LoginPage(app.driver)
        assert login.is_open()
        assert login.text("email_input") == "restore@test.kz"
