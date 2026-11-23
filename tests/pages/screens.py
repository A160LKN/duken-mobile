"""Экраны приложения. Методы — действия пользователя, а не клики по координатам."""
from framework import config
from pages.base import BasePage


class LoginPage(BasePage):
    def is_open(self, timeout: float | None = None) -> bool:
        return self.visible("login_button", timeout or 10)

    def login(self, email: str, password: str):
        self.type("email_input", email)
        self.type("password_input", password)
        self.hide_keyboard()
        self.tap("login_button")

    def open_register(self) -> "RegisterPage":
        self.tap("switch_auth_mode")
        return RegisterPage(self.driver)

    def error(self) -> str:
        return self.text("auth_error")


class RegisterPage(BasePage):
    def register(self, email: str, password: str):
        self.type("email_input", email)
        self.type("password_input", password)
        self.hide_keyboard()
        self.tap("register_button")


class NavBar(BasePage):
    def catalog(self) -> "CatalogPage":
        self.tap("nav_catalog")
        return CatalogPage(self.driver)

    def cart(self) -> "CartPage":
        self.tap("nav_cart")
        return CartPage(self.driver)

    def orders(self) -> "OrdersPage":
        self.tap("nav_orders")
        return OrdersPage(self.driver)

    def profile(self) -> "ProfilePage":
        self.tap("nav_profile")
        return ProfilePage(self.driver)

    def cart_badge(self) -> str | None:
        """Число на значке корзины — так, как его прочитает TalkBack: «Корзина, товаров: 2»."""
        from appium.webdriver.common.appiumby import AppiumBy
        nodes = self.driver.find_elements(
            AppiumBy.XPATH, "//*[@resource-id='nav_cart']/descendant-or-self::*[contains(@content-desc, ':')]")
        return nodes[0].get_attribute("content-desc").rsplit(" ", 1)[-1] if nodes else None


class CatalogPage(BasePage):
    def wait_loaded(self) -> "CatalogPage":
        self.el("product_list", timeout=15)
        return self

    def search(self, text: str):
        self.type("search_input", text)
        self.hide_keyboard()

    def product_names(self) -> list[str]:
        return [e.text for e in self.els("product_name")]

    def product_prices(self) -> list[str]:
        return [e.text for e in self.els("product_price")]

    def open_product(self, pid: int) -> "ProductPage":
        self.scroll_to("product_list", f"product_card_{pid}").click()
        return ProductPage(self.driver)

    def add_to_cart(self, pid: int):
        self.scroll_to("product_list", f"add_to_cart_{pid}")
        self.tap(f"add_to_cart_{pid}")

    def choose_category(self, name: str | None):
        tag = f"category_{name}" if name else "category_all"
        self.scroll_right_to("category_chips", tag).click()

    def sort_by(self, key: str):
        self.tap("sort_button")
        self.tap(f"sort_{key}")


class ProductPage(BasePage):
    def title(self) -> str:
        return self.text("product_title", timeout=15)

    def set_quantity(self, quantity: int):
        current = int(self.text("quantity_value"))
        while current < quantity:
            self.tap("quantity_plus"); current += 1
        while current > quantity:
            self.tap("quantity_minus"); current -= 1

    def add_to_cart(self):
        self.tap("add_to_cart_button")

    def back(self):
        self.tap("back_button")


class CartPage(BasePage):
    def wait_quote(self) -> "CartPage":
        self.el("total", timeout=max(15, config.TIMEOUT * 1.5))
        return self

    def quantity(self, pid: int) -> int:
        return int(self.text(f"qty_value_{pid}"))

    def plus(self, pid: int):
        self.tap(f"qty_plus_{pid}")

    def minus(self, pid: int):
        self.tap(f"qty_minus_{pid}")

    def remove(self, pid: int):
        self.tap(f"remove_{pid}")

    def apply_promo(self, code: str):
        self.type("promo_input", code)
        self.hide_keyboard()
        self.tap("apply_promo_button")

    def total(self) -> str:
        return self.text("total")

    def discount(self) -> str | None:
        return self.text("discount") if self.visible("discount", 3) else None

    def checkout(self) -> "PaymentPage":
        self.tap("checkout_button", timeout=15)
        return PaymentPage(self.driver)


class PaymentPage(BasePage):
    def wait_open(self) -> "PaymentPage":
        self.el("pay_button", timeout=15)
        return self

    def order_id(self) -> int:
        # «Оплата заказа №7» / «№7 тапсырысты төлеу»
        title = self.text("payment_title")
        return int(title.split("№")[1].split()[0])

    def choose_card(self, token: str):
        self.tap(f"card_{token}")

    def pay(self):
        self.tap("pay_button")

    def error(self, timeout: float = 15) -> str:
        return self.text("payment_error", timeout)


class SuccessPage(BasePage):
    def is_open(self, timeout: float = 15) -> bool:
        return self.visible("success_title", timeout)

    def order_text(self) -> str:
        return self.text("success_order_id")


class OrdersPage(BasePage):
    def status(self, order_id: int) -> str:
        return self.text(f"order_status_{order_id}", timeout=15)

    def open(self, order_id: int) -> "OrderDetailPage":
        self.tap(f"order_row_{order_id}", timeout=15)
        return OrderDetailPage(self.driver)


class OrderDetailPage(BasePage):
    def status(self) -> str:
        return self.text("order_detail_status", timeout=15)

    def cancel(self):
        self.tap("cancel_order_button")

    def pay(self) -> PaymentPage:
        self.tap("pay_order_button")
        return PaymentPage(self.driver)


class ProfilePage(BasePage):
    def email(self) -> str:
        return self.text("profile_email")

    def set_language(self, tag: str):
        self.tap(f"language_{tag}")

    def logout(self):
        self.tap("logout_button")
