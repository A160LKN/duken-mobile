import allure
import pytest

from framework.money import tenge
from pages.screens import CartPage, OrdersPage, PaymentPage, SuccessPage


def cart_with(app, *product_ids) -> CartPage:
    catalog = app.start_logged_in()
    for pid in product_ids:
        catalog.add_to_cart(pid)
    return app.nav.cart().wait_quote()


@allure.feature("Корзина и оплата")
class TestCart:
    def test_empty_cart(self, app):
        app.start_logged_in()
        cart = app.nav.cart()
        assert cart.visible("cart_empty", 5)
        cart.tap("to_catalog_button")
        assert cart.visible("product_list", 5)

    def test_total_is_calculated_by_server(self, app):
        cart = cart_with(app, 1, 2)
        expected = app.api.product(1)["price"] + app.api.product(2)["price"]
        assert cart.total() == tenge(expected)

    def test_change_quantity(self, app):
        cart = cart_with(app, 2)
        cart.plus(2)
        cart.wait(5).until(lambda _: cart.quantity(2) == 2)
        cart.wait_text("total", tenge(app.api.product(2)["price"] * 2))
        cart.minus(2)
        cart.wait(5).until(lambda _: cart.quantity(2) == 1)

    def test_minus_on_last_item_removes_line(self, app):
        cart = cart_with(app, 2)
        cart.minus(2)
        assert cart.visible("cart_empty", 5)
        assert app.nav.cart_badge() is None

    def test_cart_persists_after_restart(self, app):
        cart_with(app, 1, 7)
        app.driver.terminate_app("kz.duken.mobile")
        app.driver.activate_app("kz.duken.mobile")
        cart = app.nav.cart().wait_quote()
        assert cart.visible("cart_item_1") and cart.visible("cart_item_7")

    @pytest.mark.parametrize("code,percent", [("WELCOME10", 10), ("welcome10", 10)])
    def test_promo_code(self, app, code, percent):
        cart = cart_with(app, 1)
        price = app.api.product(1)["price"]
        cart.apply_promo(code)
        cart.wait_text("discount", tenge(price * percent // 100))
        assert cart.total() == tenge(price - price * percent // 100)

    def test_keyboard_does_not_cover_promo_field(self, app):
        """Клавиатура открыта — поле промокода и кнопка «Оформить заказ» над ней, а не под ней."""
        from framework import device
        cart = cart_with(app, 1)
        cart.tap("promo_input")
        cart.wait(5).until(lambda _: device.keyboard_top(), "клавиатура не открылась")
        top = device.keyboard_top()
        for tag in ("promo_input", "checkout_button"):
            r = cart.el(tag).rect
            assert r["y"] + r["height"] <= top, f"{tag} закрыт клавиатурой: низ {r['y'] + r['height']}, клавиатура с {top}"

    def test_unknown_promo_code(self, app):
        cart = cart_with(app, 1)
        cart.apply_promo("NOPE")
        assert cart.text("promo_error", 10) == "Промокод не найден"
        assert cart.discount() is None

    def test_promo_with_minimum_total(self, app):
        """BIG20 — от 100 000 ₸: на дешёвый товар не действует, сервер объясняет почему."""
        cart = cart_with(app, 18)
        cart.apply_promo("BIG20")
        assert "от 100 000 ₸" in cart.text("promo_error", 10)


@allure.feature("Корзина и оплата")
class TestPayment:
    @pytest.mark.smoke
    def test_full_purchase(self, app):
        cart = cart_with(app, 2)
        payment = cart.checkout().wait_open()
        order_id = payment.order_id()
        assert payment.text("order_total") == tenge(app.api.product(2)["price"])
        payment.pay()
        success = SuccessPage(app.driver)
        assert success.is_open()
        assert str(order_id) in success.order_text()
        success.tap("to_orders_button")
        assert OrdersPage(app.driver).status(order_id) == "Оплачен"
        assert app.nav.cart_badge() is None

    def test_declined_card_allows_retry_with_another(self, app):
        payment = cart_with(app, 2).checkout().wait_open()
        payment.choose_card("tok_declined")
        payment.pay()
        assert payment.error() == "Оплата отклонена: недостаточно средств"
        payment.choose_card("tok_visa")
        payment.pay()
        assert SuccessPage(app.driver).is_open()

    def test_gateway_timeout_message(self, app):
        """Банк не ответил: деньги не списаны, приложение говорит об этом и даёт повторить."""
        payment = cart_with(app, 2).checkout().wait_open()
        payment.choose_card("tok_timeout")
        payment.pay()
        assert not payment.enabled("pay_button"), "во время оплаты кнопка должна быть недоступна"
        assert "Деньги не списаны" in payment.error(timeout=20)
        assert payment.enabled("pay_button")

    @pytest.mark.stub_only
    def test_double_tap_pays_once(self, app):
        payment = cart_with(app, 2).checkout().wait_open()
        button = payment.el("pay_button")
        button.click()
        button.click()
        assert SuccessPage(app.driver).is_open()
        paid = [o for o in app.api.all_orders() if o["status"] == "paid"]
        assert len(paid) == 1 and paid[0]["payments"] == 1
        assert len(app.api.calls("POST", f"/orders/{paid[0]['id']}/pay")) == 1

    def test_back_after_payment_does_not_return_to_payment(self, app):
        """После оплаты «Назад» не открывает форму оплаты уже оплаченного заказа."""
        payment = cart_with(app, 2).checkout().wait_open()
        payment.pay()
        assert SuccessPage(app.driver).is_open()
        app.driver.back()
        page = PaymentPage(app.driver)
        # Ожидание: «Назад» уводит из оформления на вкладку приложения. Не на форму оплаты
        # и не обратно на экран успеха — иначе «Назад» становится ловушкой
        assert page.visible("nav_catalog", 5), "после «Назад» не открылась вкладка приложения"
        assert not page.visible("pay_button") and not page.visible("success_title")

    @pytest.mark.stub_only
    def test_out_of_stock_at_checkout(self, app):
        """Пока товар лежал в корзине, его раскупили: заказ не создаётся, причина понятна."""
        cart = cart_with(app, 4)
        app.api.set_stock(4, 0)
        cart.tap("checkout_button")
        assert "на складе 0" in cart.text("checkout_error", 10)


@allure.feature("Заказы")
class TestOrders:
    def test_orders_empty(self, app):
        app.start_logged_in()
        assert app.nav.orders().visible("orders_empty", 10)

    def test_pay_existing_order_from_history(self, app):
        app.start_logged_in()
        order = app.api.create_order(app.user["token"], [(7, 1)])
        orders = app.nav.orders()
        detail = orders.open(order["id"])
        assert detail.status() == "Ждёт оплаты"
        detail.pay().wait_open().pay()
        assert SuccessPage(app.driver).is_open()

    def test_cancel_order(self, app):
        app.start_logged_in()
        order = app.api.create_order(app.user["token"], [(7, 2)])
        detail = app.nav.orders().open(order["id"])
        detail.cancel()
        detail.wait_text("order_detail_status", "Отменён")
        assert not detail.visible("cancel_order_button")
