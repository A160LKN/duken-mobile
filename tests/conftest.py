"""Фикстуры: заглушка API, Appium, чистое приложение на каждый тест, артефакты при падении."""
import os
import subprocess
import sys
import time
import urllib.request

import allure
import pytest
from appium import webdriver
from appium.options.android import UiAutomator2Options

from framework import config, device
from framework.api import Api
from pages.screens import CatalogPage, LoginPage, NavBar

ROOT = config.ROOT


def _wait_http(url: str, seconds: float) -> bool:
    deadline = time.time() + seconds
    while time.time() < deadline:
        try:
            urllib.request.urlopen(url, timeout=1)
            return True
        except Exception:
            time.sleep(0.3)
    return False


@pytest.fixture(scope="session")
def api():
    client = Api()
    proc = None
    if config.BACKEND == "stub" and not client.is_up():
        proc = subprocess.Popen([sys.executable, str(ROOT / "stub-server/server.py"), "--port", config.API_URL.rsplit(":", 1)[1]])
        assert _wait_http(f"{config.API_URL}/health", 10), "заглушка API не запустилась"
    assert client.is_up(), f"API недоступен: {config.API_URL}"
    yield client
    if proc:
        proc.terminate()


@pytest.fixture(scope="session")
def appium_server():
    proc = None
    if not _wait_http(f"{config.APPIUM_URL}/status", 1):
        log = open(ROOT / "tests/appium.log", "w")
        # Appium из node_modules проекта, драйвер — в .appium/ (npm install && npm run appium:driver)
        sdk = os.environ.get("ANDROID_HOME") or os.path.expanduser("~/Library/Android/sdk")
        env = {"ANDROID_HOME": sdk, **os.environ, "APPIUM_HOME": str(ROOT / ".appium")}
        proc = subprocess.Popen([str(ROOT / "node_modules/.bin/appium"), "--port", config.APPIUM_URL.rsplit(":", 1)[1],
                                 "--log-level", "info", "--log-timestamp", "--log-no-colors"], stdout=log, stderr=subprocess.STDOUT, env=env)
        assert _wait_http(f"{config.APPIUM_URL}/status", 30), "Appium не запустился"
    yield
    if proc:
        proc.terminate()


def _new_session():
    options = UiAutomator2Options()
    options.udid = config.DEVICE_SERIAL
    options.app = config.APK_PATH
    options.app_package = config.APP_PACKAGE
    options.app_activity = config.APP_ACTIVITY
    options.no_reset = True              # данные чистит фикстура app, а не Appium
    options.auto_launch = False          # запуск — через adb с extras тестовых хуков
    options.auto_grant_permissions = False  # системные диалоги разрешений тоже проверяются
    options.new_command_timeout = 300
    options.set_capability("appium:disableWindowAnimation", True)
    drv = webdriver.Remote(config.APPIUM_URL, options=options)
    drv.implicitly_wait(0)
    # resource-id у Compose — это testTag без имени пакета: драйвер не должен дописывать «kz.duken.mobile:id/»
    drv.update_settings({"disableIdLocatorAutocompletion": True})
    return drv


class Session:
    """Сессия Appium, которая переживает сбой службы UiAutomator2 на устройстве.

    Если служба упала (так бывает на медленном эмуляторе в CI), тест, в котором это случилось, падает —
    это видно в отчёте. Но следующий тест получает новую сессию, а не каскад ошибок
    «instrumentation process is not running».
    """

    def __init__(self):
        self.driver = _new_session()

    def alive(self) -> bool:
        try:
            self.driver.get_window_size()  # команда идёт до службы на устройстве
            return True
        except Exception:
            return False

    def ensure(self):
        if not self.alive():
            try:
                self.driver.quit()
            except Exception:
                pass
            self.driver = _new_session()
        return self.driver


@pytest.fixture(scope="session")
def appium_session(appium_server):
    # Свежесобранный APK ставится явно: enforceAppInstall не переустанавливает ту же versionCode
    device.install(config.APK_PATH)
    session = Session()
    yield session
    session.driver.quit()


@pytest.fixture
def driver(appium_session):
    return appium_session.ensure()


class App:
    """Запуск приложения в нужном состоянии."""

    def __init__(self, driver, api: Api):
        self.driver, self.api = driver, api
        self.user: dict | None = None

    def _extras(self, **more) -> dict:
        return {"api_base_url": config.API_URL_FROM_DEVICE, **more}

    def start(self) -> LoginPage:
        device.launch(self._extras())
        page = LoginPage(self.driver)
        assert page.is_open(), "экран входа не открылся"
        return page

    def launch_logged_in(self, user: dict | None = None) -> CatalogPage:
        """Покупатель создаётся через API, сессия передаётся тестовым хуком — экран входа пропускается.
        Не ждёт загрузки каталога: для тестов, где каталог загрузиться не должен."""
        self.user = user or self.api.register()
        device.launch(self._extras(test_token=self.user["token"], test_email=self.user["email"]))
        return CatalogPage(self.driver)

    def start_logged_in(self, user: dict | None = None) -> CatalogPage:
        return self.launch_logged_in(user).wait_loaded()

    def open_link(self, uri: str):
        device.open_link(uri)

    @property
    def nav(self) -> NavBar:
        return NavBar(self.driver)


@pytest.fixture
def app(driver, api, request):
    if config.BACKEND == "stub":
        api.reset()
    closed = device.dismiss_foreign_anr()
    if closed:
        allure.attach(", ".join(closed), "closed-system-anr.txt", allure.attachment_type.TEXT)
    device.shell("am", "force-stop", config.APP_PACKAGE)
    device.clear_app_data()
    if not request.node.get_closest_marker("permissions"):
        # Системный диалог перекрывает экран успешной оплаты. Его проверяют тесты с маркером permissions,
        # остальным разрешение выдаётся заранее — как пользователю, который уже ответил «Разрешить»
        device.grant("android.permission.POST_NOTIFICATIONS")
    device.clear_logcat()
    # Ориентация — системная настройка: сбрасывается через adb, а не через Appium,
    # который не меняет её, пока приложение не на переднем плане
    device.shell("settings", "put", "system", "accelerometer_rotation", "0")
    device.shell("settings", "put", "system", "user_rotation", "0")
    yield App(driver, api)
    # Поворот, заданный через Appium, замораживается UiAutomation и сильнее системной настройки —
    # возвращаем его тем же способом, пока сессия жива, иначе следующий тест пойдёт в альбомной ориентации
    try:
        if driver.orientation != "PORTRAIT":
            driver.orientation = "PORTRAIT"
    except Exception:
        pass
    crashes = device.logcat_crashes()
    if crashes and config.APP_PACKAGE in crashes:
        allure.attach(crashes, "crash.txt", allure.attachment_type.TEXT)
        # Падение приложения — всегда дефект, даже если проверки теста прошли
        if not request.node.get_closest_marker("expect_crash"):
            pytest.fail(f"приложение упало во время теста:\n{crashes[-3000:]}", pytrace=False)


@pytest.fixture(autouse=True)
def _stub_only(request):
    if request.node.get_closest_marker("stub_only") and config.BACKEND != "stub":
        pytest.skip("нужен сервер-заглушка: сбои включаются по команде теста")


@pytest.hookimpl(hookwrapper=True)
def pytest_runtest_makereport(item, call):
    outcome = yield
    report = outcome.get_result()
    if report.when == "call" and report.failed and "driver" in item.fixturenames:
        drv = item.funcargs.get("driver")
        if drv is None:
            return
        try:
            allure.attach(drv.get_screenshot_as_png(), "screen.png", allure.attachment_type.PNG)
            allure.attach(drv.page_source, "page.xml", allure.attachment_type.XML)
            allure.attach(device.adb("logcat", "-d", "-t", "300", check=False), "logcat.txt", allure.attachment_type.TEXT)
        except Exception as e:  # артефакты не должны скрывать причину падения
            allure.attach(str(e), "artifacts-error.txt", allure.attachment_type.TEXT)


def pytest_configure(config):
    config.addinivalue_line("markers", "expect_crash: тест сам проверяет падение приложения")
