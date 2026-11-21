#!/usr/bin/env bash
# EM-2: エミュレータ上で MuseCal の操作シナリオを実行し、証拠（PNG・画面構造・ログ）を集める。
# .github/workflows/emulator.yml から、エミュレータ起動済みの状態で呼ばれる。
# 前提: assembleDebug と assembleDebugAndroidTest が済んでいる。adb が1台のエミュレータに繋がっている。
# 使い方: .github/scripts/em2_scenario.sh <証拠の出力先ディレクトリ>
set -u
OUT=${1:?usage: em2_scenario.sh <evidence dir>}
PKG=jp.hayashida.musecal
APP_APK=app/build/outputs/apk/debug/app-debug.apk
TEST_APK=app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
mkdir -p "$OUT/app_launch"
t0=$(date +%s)
stamp() { echo "[em2 +$(( $(date +%s) - t0 ))s] $*"; }

adb wait-for-device
adb shell getprop ro.build.version.release > "$OUT/device.txt"
adb shell wm size >> "$OUT/device.txt"
adb shell wm density >> "$OUT/device.txt"
stamp "device: $(tr '\n' ' ' < "$OUT/device.txt")"

stamp "install APKs"
adb install -r -t "$APP_APK" || exit 1
adb install -r -t "$TEST_APK" || exit 1

# 未ログイン状態にする（新しいエミュレータなので元々空だが、念のためアプリのデータを消す）
adb shell pm clear "$PKG"

# --- 1. 実アプリをランチャーと同じ入口で起動し、未ログインの案内画面を確かめる（adb + UI Automator） ---
stamp "launch app (signed out)"
adb logcat -c
adb shell am start -W -n "$PKG/.MainActivity" | tee "$OUT/app_launch/am_start.txt"
found=0
for i in $(seq 1 30); do
  adb shell uiautomator dump /sdcard/em2_launch.xml >/dev/null 2>&1
  if adb shell cat /sdcard/em2_launch.xml 2>/dev/null | grep -q 'Google でログイン'; then found=1; break; fi
  sleep 2
done
adb shell screencap -p /sdcard/em2_launch.png
adb pull /sdcard/em2_launch.png "$OUT/app_launch/01_launch_signed_out.png" >/dev/null
adb pull /sdcard/em2_launch.xml "$OUT/app_launch/01_launch_signed_out.xml" >/dev/null
adb shell dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity' > "$OUT/app_launch/resumed_activity.txt" || true
if [ "$found" = 1 ]; then
  echo "PASS: 起動後の画面構造に「Google でログイン」がある" | tee "$OUT/app_launch/result.txt"
else
  echo "FAIL: 起動後60秒以内に「Google でログイン」が画面構造に現れない" | tee "$OUT/app_launch/result.txt"
fi
adb shell am force-stop "$PKG"

# --- 2. instrumented の操作シナリオ（Compose UI Test + UI Automator） ---
stamp "run instrumented scenario"
adb shell am instrument -w -r \
  -e class jp.hayashida.musecal.SignedOutLaunchTest,jp.hayashida.musecal.MonthViewScenarioTest \
  "$PKG.test/androidx.test.runner.AndroidJUnitRunner" | tee "$OUT/instrument.txt"
inst_ok=0
if grep -q '^OK (' "$OUT/instrument.txt" && ! grep -qE 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed' "$OUT/instrument.txt"; then
  inst_ok=1
fi

# --- 3. 証拠の取り出し（テストがアプリの filesDir/em2 に書いたもの。debuggable なので run-as で読める） ---
stamp "pull evidence"
adb exec-out run-as "$PKG" tar -cf - -C files em2 > "$OUT/em2.tar" && tar -xf "$OUT/em2.tar" -C "$OUT" && rm "$OUT/em2.tar"
adb logcat -d > "$OUT/logcat.txt" || true
stamp "done"

echo "--- summary ---"
cat "$OUT/app_launch/result.txt"
echo "instrumented: $([ "$inst_ok" = 1 ] && echo PASS || echo FAIL)"
find "$OUT" -type f | sort
[ "$found" = 1 ] && [ "$inst_ok" = 1 ]
