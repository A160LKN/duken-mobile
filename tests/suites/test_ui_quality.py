"""Доступность, локализация, крупный шрифт и тёмная тема."""
import re
import xml.etree.ElementTree as ET

import allure
import pytest

from framework import config, device
from pages.screens import CatalogPage, SuccessPage
from suites.test_checkout import cart_with


def density() -> float:
    out = device.shell("wm", "density")  # «Physical density: 420» [+ «Override density: …»]
    return int(re.findall(r"(\d+)", out.splitlines()[-1])[0]) / 160


def _box(node) -> tuple[int, int, int, int]:
    return tuple(map(int, re.findall(r"\d+", node.get("bounds"))))


def clickable_nodes(driver):
    """Нажимаемые элементы приложения, кроме обрезанных краем прокручиваемого списка:
    их содержимое ещё не на экране, они проверяются после прокрутки."""
    root = ET.fromstring(driver.page_source.encode())
    parents = {child: parent for parent in root.iter() for child in parent}
    for node in root.iter():
        if node.get("clickable") != "true" or node.get("package") != config.APP_PACKAGE:
            continue
        x1, y1, x2, y2 = _box(node)
        clipped, p = False, parents.get(node)
        while p is not None and not clipped:
            if p.get("scrollable") == "true":
                px1, py1, px2, py2 = _box(p)
                clipped = y1 <= py1 or y2 >= py2
            p = parents.get(p)
        if not clipped:
            yield node


def bounds(node) -> tuple[int, int]:
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    return x2 - x1, y2 - y1


def label(node) -> str:
    """Что прочитает TalkBack: текст или описание самого узла или его потомков."""
    parts = [node.get("text") or "", node.get("content-desc") or ""]
    for child in node.iter():
        parts += [child.get("text") or "", child.get("content-desc") or ""]
    return "".join(parts).strip()


SCREENS = {
    "catalog": lambda app: app.start_logged_in(),
    "cart": lambda app: cart_with(app, 1, 2),
    "payment": lambda app: cart_with(app, 2).checkout().wait_open(),
}


@allure.feature("Доступность")
@pytest.mark.a11y
class TestAccessibility:
    @pytest.mark.parametrize("screen", SCREENS)
    def test_every_control_has_label(self, app, screen):
        SCREENS[screen](app)
        unlabeled = [n.get("resource-id") or n.get("class") for n in clickable_nodes(app.driver) if not label(n)]
        assert not unlabeled, f"TalkBack не прочитает эти элементы: {unlabeled}"

    @pytest.mark.parametrize("screen", SCREENS)
    def test_touch_targets_at_least_48dp(self, app, screen):
        SCREENS[screen](app)
        d = density()
        small = []
        for n in clickable_nodes(app.driver):
            w, h = bounds(n)
            if min(w, h) / d < 47.5:  # полпикселя — округление
                small.append(f"{n.get('resource-id') or n.get('class')}: {w / d:.0f}×{h / d:.0f} dp")
        assert not small, f"элементы меньше 48 dp: {small}"

    @pytest.fixture
    def huge_font(self):
        device.set_font_scale(2.0)
        yield
        device.set_font_scale(1.0)

    def test_checkout_reachable_with_largest_font(self, app, huge_font):
        cart = cart_with(app, 1, 2)
        cart.checkout().wait_open()
        pay = cart.el("pay_button")
        assert pay.rect["y"] + pay.rect["height"] <= app.driver.get_window_size()["height"]


@allure.feature("Тёмная тема")
class TestDarkTheme:
    @pytest.fixture
    def dark(self):
        device.set_dark_mode(True)
        yield
        device.set_dark_mode(False)

    def test_purchase_in_dark_theme(self, app, dark):
        payment = cart_with(app, 2).checkout().wait_open()
        allure.attach(app.driver.get_screenshot_as_png(), "dark-payment.png", allure.attachment_type.PNG)
        payment.pay()


@allure.feature("Локализация")
@pytest.mark.l10n
class TestKazakh:
    def test_switch_language_in_profile(self, app):
        app.start_logged_in()
        app.nav.profile().set_language("kk")
        page = CatalogPage(app.driver)
        page.wait_text("nav_cart", "Себет")
        assert "Тапсырыстар" in page.text("nav_orders")

    def test_language_kept_after_restart(self, app):
        app.start_logged_in()
        app.nav.profile().set_language("kk")
        CatalogPage(app.driver).wait_text("nav_cart", "Себет")
        app.driver.terminate_app(config.APP_PACKAGE)
        app.driver.activate_app(config.APP_PACKAGE)
        CatalogPage(app.driver).wait_text("nav_cart", "Себет")

    def test_purchase_in_kazakh(self, app):
        device.set_app_locale("kk")
        payment = cart_with(app, 2).checkout().wait_open()
        assert "тапсырысты төлеу" in payment.text("payment_title")
        payment.pay()
        success = SuccessPage(app.driver)
        assert success.is_open()
        assert success.text("success_title") == "Тапсырыс төленді"
