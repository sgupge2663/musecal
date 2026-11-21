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

# 証拠の取得に1つでも失敗したら 0 にする（最後に全体の成否へ入れる。Codex レビュー P2 への対応）
ev_ok=1
ev_fail() { ev_ok=0; echo "EVIDENCE FAIL: $*" | tee -a "$OUT/evidence_errors.txt" >&2; }

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
adb shell screencap -p /sdcard/em2_launch.png || ev_fail "screencap（起動画面）"
adb pull /sdcard/em2_launch.png "$OUT/app_launch/01_launch_signed_out.png" >/dev/null || ev_fail "adb pull 起動画面 PNG"
adb pull /sdcard/em2_launch.xml "$OUT/app_launch/01_launch_signed_out.xml" >/dev/null || ev_fail "adb pull 起動画面 XML"
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
if ! adb exec-out run-as "$PKG" tar -cf - -C files em2 > "$OUT/em2.tar"; then
  ev_fail "adb exec-out run-as tar（instrumented の証拠）"
elif ! tar -xf "$OUT/em2.tar" -C "$OUT"; then
  ev_fail "em2.tar の展開（run-as の出力が tar でない）"
fi
rm -f "$OUT/em2.tar"
adb logcat -d > "$OUT/logcat.txt" || ev_fail "adb logcat -d"

# --- 4. 必須の証拠ファイルがそろい、空でないことを確かめる ---
# 一覧は EmulatorScenarioTest.kt の capture() の並びと対応させる（ステップを足したらここにも足す）
stamp "verify evidence"
required=(
  device.txt instrument.txt logcat.txt
  app_launch/01_launch_signed_out.png app_launch/01_launch_signed_out.xml app_launch/result.txt
)
for step in em2/signed_out/01_sign_in_guide \
            em2/month_view/01_month_initial em2/month_view/02_tab_main_hidden em2/month_view/03_tab_main_shown \
            em2/month_view/04_day_popup_dec16 em2/month_view/05_popup_closed \
            em2/month_view/06_scrolled_forward em2/month_view/07_scrolled_back; do
  required+=("$step.png" "$step.xml" "${step}_semantics.txt")
done
required+=(em2/month_view/notes.txt)
: > "$OUT/evidence_check.txt"
for f in "${required[@]}"; do
  path="$OUT/$f"
  if [ ! -s "$path" ]; then
    ev_fail "必須ファイルが無いか空: $f"; echo "MISSING $f" >> "$OUT/evidence_check.txt"; continue
  fi
  case "$f" in
    *.png) [ "$(head -c 8 "$path" | od -An -tx1 | tr -d ' \n')" = "89504e470d0a1a0a" ] \
             || { ev_fail "PNG ではない: $f"; echo "BADPNG $f" >> "$OUT/evidence_check.txt"; continue; } ;;
    *.xml) grep -q '<hierarchy' "$path" \
             || { ev_fail "画面構造ダンプではない: $f"; echo "BADXML $f" >> "$OUT/evidence_check.txt"; continue; } ;;
  esac
  echo "OK $f" >> "$OUT/evidence_check.txt"
done
echo "required: ${#required[@]} files, ok: $(grep -c '^OK ' "$OUT/evidence_check.txt")" | tee -a "$OUT/evidence_check.txt"
stamp "done"

echo "--- summary ---"
cat "$OUT/app_launch/result.txt"
echo "instrumented: $([ "$inst_ok" = 1 ] && echo PASS || echo FAIL)"
echo "evidence: $([ "$ev_ok" = 1 ] && echo PASS || echo FAIL)"
find "$OUT" -type f | sort
[ "$found" = 1 ] && [ "$inst_ok" = 1 ] && [ "$ev_ok" = 1 ]
