"""Подготовка данных через API: тест создаёт покупателя и заказы напрямую, а проверяет через интерфейс."""
import uuid

import requests

from framework import config


class Api:
    def __init__(self, base: str = config.API_URL):
        self.base = base.rstrip("/")
        self.http = requests.Session()

    def _url(self, path: str) -> str:
        return f"{self.base}{path}"

    # ---------- данные ----------
    def register(self, email: str | None = None, password: str = "Duken-Test-42") -> dict:
        email = email or f"buyer-{uuid.uuid4().hex[:10]}@test.kz"
        r = self.http.post(self._url("/auth/register"), json={"email": email, "password": password}, timeout=10)
        assert r.status_code == 201, r.text
        token = self.login(email, password)
        return {"email": email, "password": password, "token": token}

    def login(self, email: str, password: str) -> str:
        r = self.http.post(self._url("/auth/login"), json={"email": email, "password": password}, timeout=10)
        assert r.status_code == 200, r.text
        return r.json()["access_token"]

    def products(self, **params) -> dict:
        return self.http.get(self._url("/products"), params=params, timeout=10).json()

    def product(self, pid: int) -> dict:
        return self.http.get(self._url(f"/products/{pid}"), timeout=10).json()

    def create_order(self, token: str, items: list[tuple[int, int]], promo: str | None = None) -> dict:
        r = self.http.post(self._url("/orders"), headers={"Authorization": f"Bearer {token}"},
                           json={"items": [{"product_id": p, "quantity": q} for p, q in items], "promo_code": promo},
                           timeout=10)
        assert r.status_code == 201, r.text
        return r.json()

    def orders(self, token: str) -> list[dict]:
        return self.http.get(self._url("/orders"), headers={"Authorization": f"Bearer {token}"}, timeout=10).json()

    # ---------- управление заглушкой ----------
    def reset(self):
        self.http.post(self._url("/__control/reset"), timeout=10).raise_for_status()

    def fault(self, path: str, *, method: str | None = None, status: int | None = None, delay: float = 0,
              drop: bool = False, drop_after: bool = False, times: int = 1, exact: bool = False, code: str | None = None, message: str | None = None):
        body = {"path": path, "status": status, "delay": delay, "drop": drop, "drop_after": drop_after,
                "times": times, "exact": exact}
        if method:
            body["method"] = method
        if code:
            body["code"] = code
        if message:
            body["message"] = message
        self.http.post(self._url("/__control/fault"), json=body, timeout=10).raise_for_status()

    def clear_faults(self):
        self.http.post(self._url("/__control/clear-faults"), timeout=10).raise_for_status()

    def expire_tokens(self):
        self.http.post(self._url("/__control/expire-tokens"), timeout=10).raise_for_status()

    def set_stock(self, pid: int, stock: int):
        self.http.post(self._url("/__control/stock"), json={"product_id": pid, "stock": stock}, timeout=10).raise_for_status()

    def requests_log(self) -> list[dict]:
        return self.http.get(self._url("/__control/requests"), timeout=10).json()

    def calls(self, method: str, path: str) -> list[dict]:
        return [r for r in self.requests_log() if r["method"] == method and r["path"] == path]

    def all_orders(self) -> list[dict]:
        return self.http.get(self._url("/__control/orders"), timeout=10).json()

    def is_up(self) -> bool:
        try:
            return self.http.get(self._url("/health"), timeout=2).ok
        except requests.RequestException:
            return False
