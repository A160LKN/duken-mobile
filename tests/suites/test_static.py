"""Проверки без устройства: ресурсы и контракт. Идут за секунды и падают раньше UI-тестов."""
import re
import xml.etree.ElementTree as ET

import pytest

from framework.config import ROOT

RES = ROOT / "app/src/main/res"


def strings(folder: str) -> dict[str, str]:
    root = ET.parse(RES / folder / "strings.xml").getroot()
    return {s.get("name"): "".join(s.itertext()) for s in root.findall("string")}


def placeholders(text: str) -> list[str]:
    return sorted(re.findall(r"%\d+\$[sd]", text))


def test_every_string_translated_to_kazakh():
    ru, kk = strings("values"), strings("values-kk")
    assert sorted(set(ru) - set(kk)) == [], "нет перевода на казахский"
    assert sorted(set(kk) - set(ru)) == [], "лишние строки в казахском"


@pytest.mark.parametrize("name", sorted(strings("values")))
def test_placeholders_match(name):
    """%1$d в русском и казахском — иначе приложение упадёт при форматировании строки."""
    ru, kk = strings("values"), strings("values-kk")
    assert placeholders(ru[name]) == placeholders(kk.get(name, ru[name]))


def test_cleartext_only_for_local_hosts():
    """HTTP без TLS разрешён только к компьютеру разработчика."""
    root = ET.parse(RES / "xml/network_security_config.xml").getroot()
    assert root.find("base-config") is None or root.find("base-config").get("cleartextTrafficPermitted") != "true"
    domains = {d.text for d in root.iter("domain")}
    assert domains <= {"10.0.2.2", "localhost"}


def test_release_build_has_no_test_hooks():
    gradle = (ROOT / "app/build.gradle.kts").read_text()
    release = gradle.split("release {")[1].split("}")[0]
    assert '"TEST_HOOKS", "false"' in release
    assert "https://" in release
