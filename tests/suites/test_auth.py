import uuid

import allure
import pytest

from pages.screens import CatalogPage, LoginPage, NavBar


@allure.feature("Вход и регистрация")
class TestAuth:
    @pytest.mark.smoke
    def test_register_opens_catalog(self, app):
        login = app.start()
        email = f"new-{uuid.uuid4().hex[:8]}@test.kz"
        login.open_register().register(email, "Duken-Test-42")
        CatalogPage(app.driver).wait_loaded()
        assert NavBar(app.driver).profile().email() == email

    def test_login_with_existing_account(self, app):
        user = app.api.register()
        app.start().login(user["email"], user["password"])
        CatalogPage(app.driver).wait_loaded()

    def test_email_is_normalized(self, app):
        """Пробелы и регистр в email не мешают войти: так его вводят с телефонной клавиатуры."""
        user = app.api.register()
        app.start().login("  " + user["email"].upper() + " ", user["password"])
        CatalogPage(app.driver).wait_loaded()

    def test_wrong_password_shows_server_message(self, app):
        user = app.api.register()
        login = app.start()
        login.login(user["email"], "Wrong-pass-1")
        assert login.error() == "Неверный email или пароль"
        assert login.is_open()

    @pytest.mark.parametrize("email,password,error_tag", [
        ("not-an-email", "Duken-Test-42", "email_error"),
        ("buyer@test.kz", "short", "password_error"),
    ])
    def test_client_side_validation(self, app, email, password, error_tag):
        login = app.start()
        login.login(email, password)
        assert login.visible(error_tag, 3)
        # Запрос с заведомо неверными данными на сервер не уходит
        assert not app.api.calls("POST", "/auth/login")

    def test_duplicate_email_on_register(self, app):
        user = app.api.register()
        register = app.start().open_register()
        register.register(user["email"], "Another-Pass-9")
        assert register.text("auth_error") == "Пользователь с таким email уже зарегистрирован"

    def test_weak_password_rejected_by_server(self, app):
        register = app.start().open_register()
        register.register(f"weak-{uuid.uuid4().hex[:6]}@test.kz", "12345678")
        assert "Слишком простой пароль" in register.text("auth_error")

    def test_password_hidden_by_default(self, app):
        login = app.start()
        login.type("password_input", "Secret-123")
        assert login.el("password_input").get_attribute("password") == "true"
        login.tap("toggle_password")
        assert login.el("password_input").get_attribute("password") == "false"

    def test_session_survives_app_restart(self, app):
        app.start_logged_in()
        app.driver.terminate_app("kz.duken.mobile")
        app.driver.activate_app("kz.duken.mobile")
        CatalogPage(app.driver).wait_loaded()

    def test_logout_clears_back_stack(self, app):
        """После выхода «Назад» не возвращает в аккаунт — приложение закрывается."""
        app.start_logged_in()
        NavBar(app.driver).profile().logout()
        login = LoginPage(app.driver)
        assert login.is_open()
        app.driver.back()
        assert app.driver.query_app_state("kz.duken.mobile") != 4  # 4 — на переднем плане

    @pytest.mark.stub_only
    def test_expired_session_returns_to_login(self, app):
        app.start_logged_in()
        app.api.expire_tokens()
        NavBar(app.driver).orders()
        login = LoginPage(app.driver)
        assert login.is_open()
        assert login.visible("session_expired_banner", 5)
