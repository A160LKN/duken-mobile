#!/usr/bin/env bash
# Локальный запуск: эмулятор, сборка, заглушка API, Appium, тесты.
#   ./scripts/run-local.sh                 — все тесты
#   ./scripts/run-local.sh -m smoke        — только смоук
#   ./scripts/run-local.sh -k payment      — по имени
set -euo pipefail
cd "$(dirname "$0")/.."
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
export ANDROID_SERIAL="${ANDROID_SERIAL:-emulator-5554}"

if ! adb devices | grep -q "$ANDROID_SERIAL.*device$"; then
  echo "Запускаю эмулятор duken_api35…"
  emulator -avd duken_api35 -no-snapshot-save -no-boot-anim -noaudio >/tmp/duken-emulator.log 2>&1 &
  adb wait-for-device
  until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done
fi

./gradlew -q assembleDebug
cd tests
[ -d .venv ] || { python3 -m venv .venv && .venv/bin/pip install -q -r requirements.txt; }
.venv/bin/python -m pytest "$@"
