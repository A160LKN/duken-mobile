#!/usr/bin/env bash
# Запуск E2E в CI: эмулятор уже запущен (android-emulator-runner), APK собран в задании build.
set -euo pipefail
cd "$(dirname "$0")/.."

adb wait-for-device
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell settings put secure show_ime_with_hard_keyboard 0

export ANDROID_SERIAL="${ANDROID_SERIAL:-emulator-5554}"

# Холодный эмулятор в CI: системный лаунчер ещё догружается и может показать «isn't responding».
# Ждём, пока система успокоится, и закрываем такие диалоги до старта тестов
adb shell input keyevent KEYCODE_WAKEUP
adb shell input keyevent KEYCODE_HOME
sleep 20
python3 -c "import sys; sys.path.insert(0, 'tests'); from framework import device; print('закрыты диалоги ANR:', device.dismiss_foreign_anr(5))"
export APK_PATH="$PWD/app/build/outputs/apk/debug/app-debug.apk"

cd tests
args=(-p no:cacheprovider --junitxml=junit.xml)
[ -n "${PYTEST_MARK:-}" ] && args+=(-m "$PYTEST_MARK")
python -m pytest "${args[@]}"
