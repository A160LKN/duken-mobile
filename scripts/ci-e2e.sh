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
export APK_PATH="$PWD/app/build/outputs/apk/debug/app-debug.apk"

cd tests
args=(-p no:cacheprovider --junitxml=junit.xml)
[ -n "${PYTEST_MARK:-}" ] && args+=(-m "$PYTEST_MARK")
python -m pytest "${args[@]}"
