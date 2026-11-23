import allure
import pytest

from framework.money import tenge
from pages.screens import CatalogPage


@allure.feature("Каталог")
class TestCatalog:
    @pytest.mark.smoke
    def test_catalog_shows_products_from_api(self, app):
        catalog = app.start_logged_in()
        first = app.api.products(limit=1)["items"][0]
        assert first["name"] in catalog.product_names()
        assert tenge(first["price"]) in catalog.product_prices()

    def test_search_filters_on_server(self, app):
        catalog = app.start_logged_in()
        catalog.search("кофе")
        catalog.wait(10).until(lambda _: catalog.product_names() and all("кофе" in n.lower() for n in catalog.product_names()))
        assert len(catalog.product_names()) == app.api.products(q="кофе")["total"]

    def test_search_without_results(self, app):
        catalog = app.start_logged_in()
        catalog.search("несуществующий товар")
        assert catalog.visible("catalog_empty", 10)
        catalog.tap("clear_search")
        catalog.wait_loaded()

    def test_search_is_debounced(self, app):
        """Запрос уходит, когда пользователь перестал печатать, а не на каждую букву."""
        catalog = app.start_logged_in()
        before = len(app.api.calls("GET", "/products"))
        catalog.type("search_input", "чайник")
        catalog.wait(10).until(lambda _: len(catalog.product_names()) == 1)
        assert len(app.api.calls("GET", "/products")) - before <= 2

    def test_category_filter(self, app):
        catalog = app.start_logged_in()
        catalog.choose_category("food")
        expected = [p["name"] for p in app.api.products(category="food")["items"]]
        catalog.wait(10).until(lambda _: sorted(catalog.product_names()) == sorted(expected))

    def test_sort_by_price(self, app):
        catalog = app.start_logged_in()
        catalog.sort_by("price_asc")
        cheapest = app.api.products(sort="price_asc", limit=1)["items"][0]["name"]
        catalog.wait(10).until(lambda _: catalog.product_names()[:1] == [cheapest])

    def test_pagination_loads_every_product(self, app):
        """В каталоге 18 товаров, страница — 20; при limit 20 всё на одной странице.
        Поэтому листаем до конца и сверяем счётчик внизу списка с total из API."""
        catalog = app.start_logged_in()
        total = app.api.products()["total"]
        end = catalog.scroll_to("product_list", "list_end")
        assert str(total) in end.text

    def test_out_of_stock_cannot_be_added(self, app):
        catalog = app.start_logged_in()
        sold_out = next(p for p in app.api.products(limit=100)["items"] if p["stock"] == 0)
        catalog.scroll_to("product_list", f"add_to_cart_{sold_out['id']}")
        assert not catalog.enabled(f"add_to_cart_{sold_out['id']}")

    def test_add_to_cart_updates_badge(self, app):
        catalog = app.start_logged_in()
        catalog.add_to_cart(1)
        assert catalog.snackbar_text() == "Добавлено в корзину"
        assert app.nav.cart_badge() == "1"
        catalog.add_to_cart(1)
        catalog.wait(5).until(lambda _: app.nav.cart_badge() == "2")

    def test_cannot_add_more_than_stock(self, app):
        catalog = app.start_logged_in()
        product = next(p for p in app.api.products(limit=100)["items"] if 0 < p["stock"] <= 4)
        for _ in range(product["stock"]):
            catalog.add_to_cart(product["id"])
        catalog.add_to_cart(product["id"])
        catalog.wait_text("snackbar", "Больше нет на складе")
        assert app.nav.cart_badge() == str(product["stock"])

    def test_product_details(self, app):
        catalog = app.start_logged_in()
        expected = app.api.product(3)
        page = catalog.open_product(3)
        assert page.title() == expected["name"]
        assert page.text("product_detail_price") == tenge(expected["price"])
        page.set_quantity(3)
        page.add_to_cart()
        assert page.snackbar_text() == "Добавлено в корзину"
        page.back()
        CatalogPage(app.driver).wait_loaded()
        assert app.nav.cart_badge() == "3"
