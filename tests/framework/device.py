"""Управление эмулятором через adb: то, что делает с телефоном пользователь или система, а не приложение."""
import subprocess
import time

from framework import config


def adb(*args: str, check: bool = True, timeout: float = 30) -> str:
    cmd = ["adb", "-s", config.DEVICE_SERIAL, *args]
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
    if check and result.returncode != 0:
        raise RuntimeError(f"{' '.join(cmd)}: {result.stderr.strip() or result.stdout.strip()}")
    return result.stdout.strip()


def shell(*args: str, check: bool = True) -> str:
    return adb("shell", *args, check=check)


def clear_app_data():
    """Как «Очистить данные» в настройках: сессия, корзина, язык и разрешения — с нуля."""
    shell("pm", "clear", config.APP_PACKAGE)


def launch(extras: dict[str, str] | None = None, uri: str | None = None):
    """Запуск приложения с extras для тестовых хуков debug-сборки или по ссылке duken://."""
    args = ["am", "start", "-W"]
    if uri:
        args += ["-a", "android.intent.action.VIEW", "-d", uri]
    args += ["-n", f"{config.APP_PACKAGE}/{config.APP_ACTIVITY}"]
    for key, value in (extras or {}).items():
        args += ["--es", key, value]
    shell(*args)


def open_link(uri: str):
    """Ссылка открывается так же, как из браузера или мессенджера: система сама выбирает приложение."""
    shell("am", "start", "-W", "-a", "android.intent.action.VIEW", "-d", uri)


def press_home():
    shell("input", "keyevent", "KEYCODE_HOME")


def press_back():
    shell("input", "keyevent", "KEYCODE_BACK")


def kill_in_background():
    """Гибель процесса в фоне: так система освобождает память. Сохранённое состояние экранов остаётся.

    am kill убивает только процесс в фоне — ровно то, что делает система, а не «force stop».
    """
    press_home()
    time.sleep(1)
    shell("am", "kill", config.APP_PACKAGE)
    for _ in range(20):
        if not shell("pidof", config.APP_PACKAGE, check=False):
            return
        time.sleep(0.25)
    raise AssertionError("процесс приложения не завершился")


def bring_to_front():
    """Возврат из списка недавних приложений: восстанавливается последняя задача, а не новый запуск."""
    shell("am", "start", "-W", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER",
          "-n", f"{config.APP_PACKAGE}/{config.APP_ACTIVITY}")


def pid() -> str:
    return shell("pidof", config.APP_PACKAGE, check=False)


def set_font_scale(scale: float):
    shell("settings", "put", "system", "font_scale", str(scale))


def set_dark_mode(on: bool):
    shell("cmd", "uimode", "night", "yes" if on else "no")


def set_app_locale(tag: str):
    """Язык приложения без смены языка системы (Android 13+)."""
    shell("cmd", "locale", "set-app-locales", config.APP_PACKAGE, "--locales", tag)


def notifications() -> list[str]:
    """Показанные уведомления приложения — записи NotificationRecord, а не упоминания пакета в настройках каналов."""
    out = shell("dumpsys", "notification", "--noredact")
    return [line.strip() for line in out.splitlines()
            if "NotificationRecord(" in line and f"pkg={config.APP_PACKAGE}" in line]


def grant(permission: str):
    shell("pm", "grant", config.APP_PACKAGE, permission)


def revoke(permission: str):
    shell("pm", "revoke", config.APP_PACKAGE, permission, check=False)


def permission_granted(permission: str) -> bool:
    out = shell("dumpsys", "package", config.APP_PACKAGE)
    return f"{permission}: granted=true" in out


def install(apk: str, replace: bool = True):
    adb("install", *(["-r"] if replace else []), apk, timeout=120)


def uninstall():
    adb("uninstall", config.APP_PACKAGE, check=False)


def logcat_crashes() -> str:
    """Падения приложения с последней очистки журнала."""
    return adb("logcat", "-d", "-b", "crash", check=False)


def clear_logcat():
    adb("logcat", "-c", "-b", "all", check=False)


def activity_count() -> int:
    """Сколько экземпляров MainActivity в задаче приложения. Должен быть один."""
    out = shell("dumpsys", "activity", "activities")
    return sum(1 for line in out.splitlines()
               if "Hist" in line and f"{config.APP_PACKAGE}/{config.APP_ACTIVITY}" in line)


def keyboard_top() -> int | None:
    """Верхняя граница экранной клавиатуры в пикселях или None, если она скрыта."""
    import re as _re
    for line in shell("dumpsys", "window").splitlines():
        m = _re.search(r"InsetsSource id=\d+ type=ime frame=\[\d+,(\d+)\]\[\d+,\d+\].*visible=true", line)
        if m:
            return int(m.group(1))
    return None


def dismiss_foreign_anr(attempts: int = 3) -> list[str]:
    """Закрыть системный диалог «… isn't responding» другого приложения (на холодном эмуляторе в CI
    так зависает Pixel Launcher и перекрывает экран). Нажимается «Wait». Диалог о зависании самого
    приложения не трогается — это дефект, его должен увидеть тест.
    Возвращает, чьи диалоги были закрыты."""
    import re as _re
    closed = []
    for _ in range(attempts):
        focus = shell("dumpsys", "window", check=False)
        m = _re.search(r"mCurrentFocus=.*Application Not Responding: ([\w.]+)", focus)
        if not m:
            break
        if m.group(1) == config.APP_PACKAGE:
            break
        shell("uiautomator", "dump", "/sdcard/anr.xml", check=False)
        xml = shell("cat", "/sdcard/anr.xml", check=False)
        b = _re.search(r'resource-id="android:id/aerr_wait"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
        if not b:
            break
        x, y = (int(b[1]) + int(b[3])) // 2, (int(b[2]) + int(b[4])) // 2
        shell("input", "tap", str(x), str(y))
        closed.append(m.group(1))
        time.sleep(1)
    return closed
