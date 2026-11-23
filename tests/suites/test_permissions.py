"""Разрешение на уведомления (Android 13+): спрашиваем после первой оплаты, отказ ничего не ломает."""
import allure
import pytest

from framework import device
from pages.base import BasePage
from pages.screens import SuccessPage
from suites.test_checkout import cart_with

PERMISSION = "android.permission.POST_NOTIFICATIONS"
ALLOW = "com.android.permissioncontroller:id/permission_allow_button"
DENY = "com.android.permissioncontroller:id/permission_deny_button"


def pay_new_order(app, pid: int = 2):
    """Первая покупка с запуска приложения"""
    cart_with(app, pid).checkout().wait_open().pay()


def _pay_more(app, pid):
    """Ещё одна покупка в уже запущенном приложении"""
    catalog = app.nav.catalog().wait_loaded()
    catalog.add_to_cart(pid)
    app.nav.cart().wait_quote().checkout().wait_open().pay()


@allure.feature("Разрешения")
@pytest.mark.permissions
class TestNotificationsPermission:
    def test_not_asked_before_purchase(self, app):
        app.start_logged_in()
        assert not BasePage(app.driver).visible(ALLOW, 3)

    def test_allow_then_notification_on_next_payment(self, app):
        pay_new_order(app)
        page = BasePage(app.driver)
        page.tap(ALLOW, timeout=10)
        assert SuccessPage(app.driver).is_open()
        assert device.permission_granted(PERMISSION)
        page.tap("continue_shopping_button")
        _pay_more(app, 7)
        assert SuccessPage(app.driver).is_open()
        page.wait(10).until(lambda _: device.notifications())

    def test_deny_keeps_app_working(self, app):
        pay_new_order(app)
        page = BasePage(app.driver)
        page.tap(DENY, timeout=10)
        assert SuccessPage(app.driver).is_open()
        assert not device.permission_granted(PERMISSION)
        assert device.notifications() == []

    def test_already_granted_no_dialog(self, app):
        app.start_logged_in()
        device.grant(PERMISSION)
        _pay_more(app, 2)
        assert SuccessPage(app.driver).is_open()
        assert not BasePage(app.driver).visible(ALLOW, 2)
