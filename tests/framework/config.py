"""Настройки запуска. Всё задаётся переменными окружения, значения по умолчанию — для локального эмулятора."""
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

APPIUM_URL = os.getenv("APPIUM_URL", "http://127.0.0.1:4723")
APK_PATH = os.getenv("APK_PATH", str(ROOT / "app/build/outputs/apk/debug/app-debug.apk"))
APP_PACKAGE = "kz.duken.mobile"
APP_ACTIVITY = ".MainActivity"
DEVICE_SERIAL = os.getenv("ANDROID_SERIAL", "emulator-5554")

# Сервер для тестов: с компьютера — localhost, с эмулятора тот же компьютер виден как 10.0.2.2
API_URL = os.getenv("API_URL", "http://127.0.0.1:8000")
API_URL_FROM_DEVICE = os.getenv("API_URL_FROM_DEVICE", "http://10.0.2.2:8000/")
# stub — сервер-заглушка из stub-server/ (сбои по команде), real — настоящий shop-api
BACKEND = os.getenv("BACKEND", "stub")

TIMEOUT = float(os.getenv("UI_TIMEOUT", "10"))
