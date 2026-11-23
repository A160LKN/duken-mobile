"""Сервер-заглушка с контрактом shop-api для автотестов мобильного приложения.

Зачем он, если есть настоящий shop-api:
- тест сам выбирает ответ сервера: задержка, 500, обрыв соединения, истёкший токен;
- состояние сбрасывается одной командой, тесты не зависят друг от друга;
- работает без Docker и PostgreSQL, на чистой стандартной библиотеке Python.

Контракт тот же, что у shop-api (пути, поля, коды ошибок). В CI те же тесты
дополнительно идут против настоящего shop-api, так что расхождение не останется незамеченным.

Управление — эндпоинты /__control/*, их вызывают только тесты:
  POST /__control/reset                    — вернуть каталог, пользователей и заказы к исходному виду
  POST /__control/fault                    — {"path": "/products", "exact": false, "method": "GET",
                                              "status": 500, "delay": 0, "drop": false, "times": 1}
                                              times: -1 — сбой до clear-faults
                                              drop_after: true — выполнить запрос и оборвать ответ
  POST /__control/expire-tokens            — все выданные токены становятся недействительными
  POST /__control/stock                    — {"product_id": 4, "stock": 0}
  GET  /__control/requests                 — журнал запросов: метод, путь, код ответа
Запуск: python3 server.py --port 8000
"""
from __future__ import annotations

import argparse
import json
import re
import secrets
import threading
import time
from datetime import UTC, datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

PRODUCTS = [
    ("Смартфон Samsung Galaxy A56", "electronics", 189_990_00, 25),
    ("Наушники JBL Tune 520BT", "electronics", 24_990_00, 60),
    ("Смарт-часы Xiaomi Watch S4", "electronics", 64_990_00, 14),
    ("Портативная колонка Marshall Emberton III", "electronics", 89_990_00, 4),
    ("Электронная книга PocketBook Verse Pro", "electronics", 79_990_00, 9),
    ("Powerbank Anker 20000 мА·ч", "electronics", 19_990_00, 0),
    ("Чайник Xiaomi Mi Kettle", "home", 12_490_00, 40),
    ("Пылесос Dreame T20", "home", 129_990_00, 8),
    ("Кофеварка De'Longhi Dedica EC685", "home", 119_990_00, 3),
    ("Увлажнитель воздуха Xiaomi Humidifier 2", "home", 22_990_00, 18),
    ("Набор посуды Tefal Ingenio, 10 предметов", "home", 74_990_00, 11),
    ("Рюкзак городской 20 л", "accessories", 15_990_00, 30),
    ("Термокружка Stanley 470 мл", "accessories", 17_490_00, 22),
    ("Зонт складной автоматический", "accessories", 8_990_00, 35),
    ("Кофе в зёрнах 1 кг", "food", 9_990_00, 100),
    ("Чай зелёный жасминовый, 100 пакетиков", "food", 3_490_00, 70),
    ("Мёд горный алтайский, 500 г", "food", 5_990_00, 26),
    ("Курт солёный, 300 г", "food", 2_490_00, 45),
]
PROMOS = {"WELCOME10": (10, 0), "BIG20": (20, 100_000_00)}  # код → (скидка %, минимальная сумма)
COMMON_PASSWORDS = {"12345678", "123456789", "password", "qwerty123", "11111111", "00000000", "1q2w3e4r"}
EMAIL = re.compile(r"^[^@\s]+@[^@\s]+\.[^@\s]+$")
PAYMENT_TIMEOUT = 5  # shop-api ждёт шлюз 5 секунд и отвечает 502


class ApiError(Exception):
    def __init__(self, status: int, code: str, message: str):
        self.status, self.code, self.message = status, code, message


class State:
    def __init__(self):
        self.lock = threading.RLock()
        self.reset()

    def reset(self):
        with self.lock:
            self.products = {i: {"id": i, "name": n, "category": c, "price": p, "stock": s}
                             for i, (n, c, p, s) in enumerate(PRODUCTS, start=1)}
            self.users: dict[str, dict] = {}
            self.tokens: dict[str, str] = {}
            self.orders: dict[int, dict] = {}
            self.next_order = 1
            self.faults: list[dict] = []
            self.log: list[dict] = []
            self.paying: set[int] = set()


state = State()


def now_iso() -> str:
    return datetime.now(UTC).isoformat()


def validation(msg: str) -> ApiError:
    return ApiError(422, "validation", msg)


def credentials(body: dict) -> tuple[str, str]:
    if not isinstance(body, dict) or set(body) - {"email", "password"}:
        raise validation("неизвестные поля")
    email = str(body.get("email", "")).strip().lower()
    password = str(body.get("password", ""))
    if not EMAIL.match(email) or not 8 <= len(password) <= 128:
        raise validation("неверный email или пароль короче 8 символов")
    return email, password


def priced(items: list, promo: str | None) -> dict:
    if not isinstance(items, list) or not 1 <= len(items) <= 50:
        raise validation("items: от 1 до 50 строк")
    ids = [i.get("product_id") for i in items]
    if len(ids) != len(set(ids)):
        raise validation("товар повторяется — укажите его один раз с нужным количеством")
    lines, subtotal = [], 0
    for line in items:
        pid, qty = line.get("product_id"), line.get("quantity")
        if not isinstance(qty, int) or not 1 <= qty <= 100:
            raise validation("quantity: от 1 до 100")
        product = state.products.get(pid)
        if product is None:
            raise ApiError(404, "product_not_found", f"Товар с id {pid} не найден")
        if product["stock"] < qty:
            raise ApiError(409, "out_of_stock", f"«{product['name']}»: на складе {product['stock']} шт.")
        lines.append({"product_id": pid, "name": product["name"], "quantity": qty, "price": product["price"]})
        subtotal += product["price"] * qty
    discount, code = 0, None
    if promo:
        code = promo.strip().upper()
        if code not in PROMOS:
            raise ApiError(422, "promo_invalid", "Промокод не найден")
        percent, minimum = PROMOS[code]
        if subtotal < minimum:
            raise ApiError(422, "promo_invalid", f"Промокод действует для заказа от {minimum // 100:,} ₸".replace(",", " "))
        discount = subtotal * percent // 100
    return {"lines": lines, "subtotal": subtotal, "discount": discount, "total": subtotal - discount, "promo_code": code}


def order_out(o: dict) -> dict:
    return {k: o[k] for k in ("id", "status", "items", "subtotal", "discount", "total", "promo_code", "created_at")}


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "duken-stub"
    # Как uvicorn у shop-api: простаивающее keep-alive соединение закрывается через 5 секунд.
    # Без этого клиент никогда не встречает соединение, закрытое сервером, и такой дефект не виден
    timeout = 5

    def log_message(self, *_):  # журнал пишется в state.log, а не в stderr
        pass

    # ---------- транспорт ----------
    def _send(self, status: int, payload) -> None:
        body = b"" if payload is None else json.dumps(payload, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _body(self):
        length = int(self.headers.get("Content-Length") or 0)
        if not length:
            return {}
        try:
            return json.loads(self.rfile.read(length))
        except json.JSONDecodeError:
            raise validation("тело запроса — не JSON") from None

    def _user(self) -> dict:
        auth = self.headers.get("Authorization", "")
        email = state.tokens.get(auth.removeprefix("Bearer ").strip())
        if not email:
            raise ApiError(401, "unauthorized", "Требуется вход")
        return state.users[email]

    def _fault(self, method: str, path: str) -> dict | None:
        with state.lock:
            for f in state.faults:
                matches = path == f["path"] if f.get("exact") else path.startswith(f["path"])
                if matches and f.get("method", method) == method and f["times"] != 0:
                    f["times"] -= 1
                    return f
        return None

    def _handle(self, method: str):
        url = urlparse(self.path)
        path = url.path.rstrip("/") or "/"
        status = 500
        try:
            fault = None
            if not path.startswith("/__control") and (fault := self._fault(method, path)):
                if fault.get("delay"):
                    time.sleep(fault["delay"])
                if fault.get("drop"):
                    self.close_connection = True
                    self.connection.shutdown(2)  # обрыв без ответа: клиент видит IOException
                    status = 0
                    return
                if fault.get("status"):
                    status = fault["status"]
                    self._send(status, {"error": {"code": fault.get("code", "fault"),
                                                  "message": fault.get("message", "Внутренняя ошибка сервера")}})
                    return
            status, payload = self.route(method, path, parse_qs(url.query))
            if fault and fault.get("drop_after"):
                # Сервер всё сделал, но ответ потерялся в сети: клиент не знает, прошла ли операция
                self.close_connection = True
                self.connection.shutdown(2)
                return
            self._send(status, payload)
        except ApiError as e:
            status = e.status
            if e.code == "validation":
                self._send(422, {"detail": [{"msg": e.message}]})
            else:
                self._send(e.status, {"error": {"code": e.code, "message": e.message}})
        finally:
            if not path.startswith("/__control"):
                with state.lock:
                    state.log.append({"method": method, "path": path, "status": status, "at": time.time()})

    def do_GET(self):
        self._handle("GET")

    def do_POST(self):
        self._handle("POST")

    # ---------- маршруты ----------
    def route(self, method: str, path: str, query: dict):
        q = {k: v[0] for k, v in query.items()}
        parts = path.strip("/").split("/")
        with state.lock:
            match method, parts:
                case "GET", ["health"]:
                    return 200, {"status": "ok"}

                case "POST", ["__control", "reset"]:
                    state.reset()
                    return 200, {"ok": True}
                case "POST", ["__control", "fault"]:
                    f = self._body()
                    f.setdefault("times", 1)
                    state.faults.append(f)
                    return 200, {"ok": True}
                case "POST", ["__control", "clear-faults"]:
                    state.faults.clear()
                    return 200, {"ok": True}
                case "POST", ["__control", "expire-tokens"]:
                    state.tokens.clear()
                    return 200, {"ok": True}
                case "POST", ["__control", "stock"]:
                    b = self._body()
                    state.products[b["product_id"]]["stock"] = b["stock"]
                    return 200, state.products[b["product_id"]]
                case "GET", ["__control", "requests"]:
                    return 200, state.log
                case "GET", ["__control", "orders"]:
                    return 200, [order_out(o) | {"payments": o["payments"]} for o in state.orders.values()]

                case "POST", ["auth", "register"]:
                    email, password = credentials(self._body())
                    if password.lower() in COMMON_PASSWORDS or len(set(password)) < 4:
                        raise ApiError(422, "weak_password", "Слишком простой пароль: возьмите длиннее и не из популярных")
                    if email in state.users:
                        raise ApiError(409, "email_taken", "Пользователь с таким email уже зарегистрирован")
                    user = {"id": len(state.users) + 1, "email": email, "role": "customer", "password": password}
                    state.users[email] = user
                    return 201, {k: user[k] for k in ("id", "email", "role")}
                case "POST", ["auth", "login"]:
                    email, password = credentials(self._body())
                    user = state.users.get(email)
                    if user is None or user["password"] != password:
                        raise ApiError(401, "bad_credentials", "Неверный email или пароль")
                    token = secrets.token_urlsafe(24)
                    state.tokens[token] = email
                    return 200, {"access_token": token, "token_type": "bearer"}
                case "GET", ["auth", "me"]:
                    u = self._user()
                    return 200, {k: u[k] for k in ("id", "email", "role")}

                case "GET", ["products"]:
                    items = list(state.products.values())
                    if c := q.get("category"):
                        items = [p for p in items if p["category"] == c]
                    if s := q.get("q"):
                        items = [p for p in items if s.lower() in p["name"].lower()]
                    sort = q.get("sort", "default")
                    keys = {"price_asc": lambda p: (p["price"], p["id"]), "price_desc": lambda p: (-p["price"], p["id"]),
                            "name": lambda p: p["name"].lower(), "newest": lambda p: -p["id"]}
                    if sort in keys:
                        items.sort(key=keys[sort])
                    elif sort != "default":
                        raise validation("sort: неизвестное значение")
                    limit, offset = int(q.get("limit", 20)), int(q.get("offset", 0))
                    if not 1 <= limit <= 100 or offset < 0:
                        raise validation("limit: 1..100, offset >= 0")
                    return 200, {"items": items[offset:offset + limit], "total": len(items), "limit": limit, "offset": offset}
                case "GET", ["products", "categories"]:
                    counts: dict[str, int] = {}
                    for p in state.products.values():
                        counts[p["category"]] = counts.get(p["category"], 0) + 1
                    return 200, [{"name": k, "count": v} for k, v in sorted(counts.items())]
                case "GET", ["products", pid] if pid.isdigit():
                    product = state.products.get(int(pid))
                    if product is None:
                        raise ApiError(404, "not_found", "Товар не найден")
                    return 200, product

                case "POST", ["orders", "quote"]:
                    self._user()
                    b = self._body()
                    p = priced(b.get("items"), b.get("promo_code"))
                    return 200, {k: p[k] for k in ("subtotal", "discount", "total", "promo_code")}
                case "POST", ["orders"]:
                    user = self._user()
                    b = self._body()
                    p = priced(b.get("items"), b.get("promo_code"))
                    for line in p["lines"]:
                        state.products[line["product_id"]]["stock"] -= line["quantity"]
                    order = {"id": state.next_order, "owner": user["email"], "status": "created", "items": p["lines"],
                             "subtotal": p["subtotal"], "discount": p["discount"], "total": p["total"],
                             "promo_code": p["promo_code"], "created_at": now_iso(), "payments": 0}
                    state.orders[order["id"]] = order
                    state.next_order += 1
                    return 201, order_out(order)
                case "GET", ["orders"]:
                    user = self._user()
                    return 200, [order_out(o) for o in state.orders.values() if o["owner"] == user["email"]]
                case "GET", ["orders", oid] if oid.isdigit():
                    return 200, order_out(self._own_order(int(oid)))
                case "POST", ["orders", oid, "pay"] if oid.isdigit():
                    return self._pay(int(oid), self._body())
                case "POST", ["orders", oid, "cancel"] if oid.isdigit():
                    order = self._own_order(int(oid))
                    if order["status"] not in ("created", "paid"):
                        raise ApiError(409, "invalid_status", f"Нельзя отменить заказ в статусе {order['status']}")
                    order["status"] = "cancelled"
                    for line in order["items"]:
                        state.products[line["product_id"]]["stock"] += line["quantity"]
                    return 200, order_out(order)
        raise ApiError(404, "not_found", "Нет такого адреса")

    def _own_order(self, oid: int) -> dict:
        user = self._user()
        order = state.orders.get(oid)
        if order is None or order["owner"] != user["email"]:
            raise ApiError(404, "not_found", "Заказ не найден")
        return order

    def _pay(self, oid: int, body: dict):
        token = str(body.get("card_token", ""))
        if not 3 <= len(token) <= 64:
            raise validation("card_token: от 3 до 64 символов")
        order = self._own_order(oid)
        if order["status"] != "created":
            raise ApiError(409, "invalid_status", "Заказ уже оплачен или отменён")
        if oid in state.paying:
            raise ApiError(409, "payment_in_progress", "Оплата заказа уже идёт — дождитесь результата")
        state.paying.add(oid)
        state.lock.release()  # шлюз «думает» без блокировки остальных запросов
        try:
            if token == "tok_timeout":
                time.sleep(PAYMENT_TIMEOUT)
            else:
                time.sleep(0.3)
        finally:
            state.lock.acquire()
            state.paying.discard(oid)
        if token == "tok_timeout":
            raise ApiError(502, "payment_unavailable", "Платёжный сервис не отвечает. Деньги не списаны — попробуйте ещё раз позже")
        if token == "tok_declined":
            raise ApiError(402, "payment_declined", "Оплата отклонена: недостаточно средств")
        order["status"] = "paid"
        order["payments"] += 1
        return 200, order_out(order)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8000)
    args = parser.parse_args()
    server = ThreadingHTTPServer((args.host, args.port), Handler)
    print(f"stub shop-api on http://{args.host}:{args.port}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
