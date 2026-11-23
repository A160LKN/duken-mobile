"""Сеть на телефоне ненадёжна: метро, лифт, переключение Wi-Fi → LTE. Сервер тоже бывает недоступен."""
import time

import allure
import pytest

from framework import device
from pages.screens import CatalogPage, SuccessPage
from suites.test_checkout import cart_with


@pytest.fixture
def offline(app):
    """Авиарежим на устройстве. Возвращается обратно, даже если тест упал."""
    def set_airplane(on: bool):
        device.shell("cmd", "connectivity", "airplane-mode", "enable" if on else "disable")
        # Ждём фактического состояния сети, а не фиксированное время: Wi-Fi эмулятора поднимается 2–10 с
        deadline = time.time() + 20
        while time.time() < deadline:
            reachable = device.shell("ping", "-c", "1", "-W", "1", "10.0.2.2", check=False).find("1 received") >= 0
            if reachable != on:
                return
            time.sleep(0.5)
        raise AssertionError(f"сеть устройства не перешла в состояние airplane={on}")
    yield set_airplane
    set_airplane(False)


@allure.feature("Сеть")
@pytest.mark.network
class TestNetwork:
    def test_catalog_offline_then_retry(self, app, offline):
        offline(True)
        page = app.launch_logged_in()
        assert page.text("error_text", 15) == "Нет подключения к интернету"
        offline(False)
        page.tap("retry_button")
        page.wait_loaded()

    def test_login_offline_message(self, app, offline):
        user = app.api.register()
        login = app.start()
        offline(True)
        login.login(user["email"], user["password"])
        assert login.error() == "Нет подключения к интернету"
        offline(False)
        login.tap("login_button")
        CatalogPage(app.driver).wait_loaded()

    @pytest.mark.stub_only
    def test_server_error_shows_retry(self, app):
        app.api.fault("/products", exact=True, status=500, times=1)
        page = app.launch_logged_in()
        assert page.text("error_text", 15) == "Внутренняя ошибка сервера"
        page.tap("retry_button")
        page.wait_loaded()

    @pytest.mark.stub_only
    def test_connection_dropped_mid_request(self, app):
        app.api.fault("/products", exact=True, drop=True, times=1)
        page = app.launch_logged_in()
        assert page.visible("retry_button", 15)
        page.tap("retry_button")
        page.wait_loaded()

    @pytest.mark.stub_only
    def test_slow_server_shows_progress_not_freeze(self, app):
        """Медленный ответ: виден индикатор загрузки, интерфейс отвечает (ANR нет)."""
        app.api.fault("/products", exact=True, delay=4, times=1)
        page = app.launch_logged_in()
        assert page.visible("loading", 3)
        page.tap("nav_profile")  # интерфейс не завис, пока идёт запрос
        assert page.visible("profile_email", 3)
        page.tap("nav_catalog")
        page.wait_loaded()

    @pytest.mark.stub_only
    def test_network_lost_during_payment(self, app):
        """Связь оборвалась, пока шёл ответ на оплату. Повтор не должен списать деньги дважды."""
        payment = cart_with(app, 2).checkout().wait_open()
        app.api.fault("/orders/", method="POST", drop=True, times=1)
        payment.pay()
        assert payment.error() == "Нет подключения к интернету"
        payment.pay()
        assert SuccessPage(app.driver).is_open()
        paid = [o for o in app.api.all_orders() if o["status"] == "paid"]
        assert len(paid) == 1 and paid[0]["payments"] == 1
        # Обрыв — и повтор только по кнопке: HTTP-клиент не переотправляет оплату сам
        assert len(app.api.calls("POST", f"/orders/{paid[0]['id']}/pay")) == 2

    @pytest.mark.stub_only
    def test_payment_response_lost_after_charge(self, app):
        """Оплата прошла, но ответ потерялся. Повтор не списывает второй раз и не пугает ошибкой:
        приложение видит, что заказ уже оплачен, и показывает успех."""
        payment = cart_with(app, 2).checkout().wait_open()
        order_id = payment.order_id()
        app.api.fault(f"/orders/{order_id}/pay", drop_after=True, times=1)
        payment.pay()
        assert payment.error() == "Нет подключения к интернету"
        payment.pay()
        assert SuccessPage(app.driver).is_open()
        order = next(o for o in app.api.all_orders() if o["id"] == order_id)
        assert order["status"] == "paid" and order["payments"] == 1
        # Ровно две попытки — обе от пользователя, без тихого повтора HTTP-клиентом
        assert len(app.api.calls("POST", f"/orders/{order_id}/pay")) == 2

    def test_request_after_idle_keepalive_closed(self, app):
        """Сервер закрыл простаивающее соединение (uvicorn — через 5 с). Следующий запрос не должен
        показывать «нет интернета»: клиент открывает новое соединение."""
        catalog = app.start_logged_in()
        catalog.add_to_cart(2)
        time.sleep(7)  # дольше keep-alive сервера
        cart = app.nav.cart()
        cart.wait_quote()
        assert not cart.visible("quote_error")

    @pytest.mark.stub_only
    def test_filter_error_then_retry(self, app):
        """Ошибка сервера при смене фильтра: понятный текст с сервера и повтор с тем же фильтром."""
        catalog = app.start_logged_in()
        names = catalog.product_names()
        app.api.fault("/products", exact=True, status=503, times=1, message="Сервис перегружен")
        catalog.choose_category("food")
        assert catalog.text("error_text", 10) == "Сервис перегружен"
        catalog.tap("retry_button")
        expected = sorted(p["name"] for p in app.api.products(category="food")["items"])
        catalog.wait(10).until(lambda _: sorted(catalog.product_names()) == expected)
        assert sorted(names) != expected
