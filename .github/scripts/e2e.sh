#!/usr/bin/env bash
# Сквозные тесты на эмуляторе: обновление с прежней версии и главные сценарии.
#
# Пакеты лежат в build/e2e-apks:
#   bolh-previous.apk  — прежняя версия, собранная из её .aab;
#   bolh-new.apk       — новая версия, release-сборка с R8;
#   bolh-e2e-tests.apk — сами тесты.
# Итоги, скриншоты сбоев и журналы складываются в e2e-results.
set -uo pipefail

APKS=build/e2e-apks
OUT=e2e-results
APP=eu.bolh.app
RUNNER=eu.bolh.e2e/androidx.test.runner.AndroidJUnitRunner
failed=0
mkdir -p "$OUT"

adb wait-for-device
# Экранная клавиатура нужна и тогда, когда эмулятор считает, что есть аппаратная:
# тест проверяет, что поле не уходит под неё.
adb shell settings put secure show_ime_with_hard_keyboard 1
adb shell rm -rf /data/local/tmp/e2e
adb logcat -c || true

instrument() {
  local name=$1
  shift
  echo "::group::$name"
  adb shell am instrument -w "$@" "$RUNNER" | tee "$OUT/$name.txt"
  echo "::endgroup::"
  if grep -q "^OK (" "$OUT/$name.txt" && ! grep -qE "INSTRUMENTATION_FAILED|Process crashed" "$OUT/$name.txt"; then
    echo "$name: passed"
  else
    echo "::error::$name failed"
    failed=1
  fi
}

adb install -r -t "$APKS/bolh-e2e-tests.apk"

# Обновление: прежняя версия получает данные через экран, новая ставится поверх.
adb uninstall "$APP" >/dev/null 2>&1 || true
adb install "$APKS/bolh-previous.apk"
instrument upgrade-seed -e class eu.bolh.e2e.upgrade.SeedBeforeUpdateTest
if adb install -r "$APKS/bolh-new.apk"; then
  instrument upgrade-verify -e class eu.bolh.e2e.upgrade.VerifyAfterUpdateTest
else
  echo "::error::the new version did not install over the previous one"
  failed=1
  adb uninstall "$APP" >/dev/null 2>&1 || true
  adb install "$APKS/bolh-new.apk"
fi

# Главные сценарии, каждый с чистой установки.
instrument smoke -e package eu.bolh.e2e.smoke

adb pull /data/local/tmp/e2e "$OUT/screens" >/dev/null 2>&1 || true
adb logcat -d > "$OUT/logcat.txt" 2>&1 || true
adb logcat -d -b crash > "$OUT/crash.txt" 2>&1 || true
if grep -q "$APP" "$OUT/crash.txt"; then
  echo "::error::Bolh crashed, see crash.txt"
  failed=1
fi
exit $failed
