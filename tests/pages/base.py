"""Общие действия со страницей. Локаторы — resource-id из testTag Compose: не зависят от языка и вёрстки."""
from appium.webdriver.common.appiumby import AppiumBy
from selenium.common.exceptions import NoSuchElementException, StaleElementReferenceException, TimeoutException
from selenium.webdriver.support import expected_conditions as ec
from selenium.webdriver.support.ui import WebDriverWait

from framework import config


class BasePage:
    def __init__(self, driver):
        self.driver = driver

    # ---------- поиск ----------
    @staticmethod
    def by_id(tag: str):
        return AppiumBy.ID, tag

    def wait(self, timeout: float | None = None) -> WebDriverWait:
        return WebDriverWait(self.driver, timeout or config.TIMEOUT, ignored_exceptions=[StaleElementReferenceException])

    def el(self, tag: str, timeout: float | None = None):
        return self.wait(timeout).until(ec.presence_of_element_located(self.by_id(tag)), f"нет элемента {tag}")

    def els(self, tag: str):
        return self.driver.find_elements(*self.by_id(tag))

    def visible(self, tag: str, timeout: float = 0) -> bool:
        try:
            if timeout:
                self.el(tag, timeout)
                return True
            return bool(self.els(tag))
        except TimeoutException:
            return False

    def gone(self, tag: str, timeout: float | None = None):
        self.wait(timeout).until_not(lambda d: d.find_elements(*self.by_id(tag)), f"{tag} не исчез")

    def text(self, tag: str, timeout: float | None = None) -> str:
        """Текст элемента. У Compose текст бывает у дочернего узла — берём первый непустой."""
        element = self.el(tag, timeout)
        own = element.text
        if own:
            return own
        for child in element.find_elements(AppiumBy.XPATH, ".//*[@text!='']"):
            if child.text:
                return child.text
        return ""

    def wait_text(self, tag: str, expected: str, timeout: float | None = None):
        self.wait(timeout).until(lambda _: expected in self._safe_text(tag), f"в {tag} нет «{expected}»")

    def _safe_text(self, tag: str) -> str:
        try:
            return self.text(tag, timeout=0.5)
        except TimeoutException:
            return ""

    # ---------- действия ----------
    def tap(self, tag: str, timeout: float | None = None):
        self.wait(timeout).until(ec.element_to_be_clickable(self.by_id(tag)), f"{tag} недоступен").click()

    def type(self, tag: str, value: str, clear: bool = True):
        field = self.el(tag)
        field.click()
        if clear:
            field.clear()
        field.send_keys(value)

    def enabled(self, tag: str) -> bool:
        return self.el(tag).get_attribute("enabled") == "true"

    def hide_keyboard(self):
        if self.driver.is_keyboard_shown():
            self.driver.hide_keyboard()

    def _swipe_up(self, rect: dict, fraction: float = 0.6):
        """Медленный свайп с паузой перед отпусканием: список сдвигается ровно на палец, без инерции."""
        x = rect["x"] + rect["width"] // 2
        self._drag((x, rect["y"] + int(rect["height"] * (0.5 + fraction / 2))),
                   (x, rect["y"] + int(rect["height"] * (0.5 - fraction / 2))))

    def _swipe_left(self, rect: dict, fraction: float = 0.6):
        y = rect["y"] + rect["height"] // 2
        self._drag((rect["x"] + int(rect["width"] * (0.5 + fraction / 2)), y),
                   (rect["x"] + int(rect["width"] * (0.5 - fraction / 2)), y))

    def _drag(self, start: tuple[int, int], end: tuple[int, int]):
        from selenium.webdriver.common.actions import interaction
        from selenium.webdriver.common.actions.action_builder import ActionBuilder
        from selenium.webdriver.common.actions.pointer_input import PointerInput

        actions = ActionBuilder(self.driver, mouse=PointerInput(interaction.POINTER_TOUCH, "finger"), duration=600)
        actions.pointer_action.move_to_location(*start).pointer_down()
        actions.pointer_action.move_to_location(*end).pause(0.2).release()
        actions.perform()

    def scroll_to(self, list_tag: str, item_tag: str, max_swipes: int = 15):
        """Прокрутить список до элемента. Ленивый список Compose создаёт элементы только на экране."""
        for _ in range(max_swipes):
            found = self.els(item_tag)
            if found and self._on_screen(found[0], list_tag):
                return found[0]
            self._swipe_up(self.el(list_tag).rect)
        raise NoSuchElementException(f"{item_tag} не найден в {list_tag}")

    def scroll_right_to(self, row_tag: str, item_tag: str, max_swipes: int = 6):
        """Горизонтальный ряд (категории): листать влево, пока элемент не окажется целиком на экране."""
        for _ in range(max_swipes):
            found = self.els(item_tag)
            if found:
                r, box = found[0].rect, self.el(row_tag).rect
                if r["x"] >= box["x"] and r["x"] + r["width"] <= box["x"] + box["width"]:
                    return found[0]
            self._swipe_left(self.el(row_tag).rect)
        raise NoSuchElementException(f"{item_tag} не найден в {row_tag}")

    def _on_screen(self, element, list_tag: str) -> bool:
        """Элемент целиком внутри списка, а не обрезан нижней панелью."""
        try:
            r, box = element.rect, self.el(list_tag).rect
        except StaleElementReferenceException:
            return False
        return r["y"] >= box["y"] and r["y"] + r["height"] <= box["y"] + box["height"]

    def snackbar_text(self, timeout: float | None = None) -> str:
        return self.text("snackbar", timeout)
