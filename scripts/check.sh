#!/bin/sh
# ローカル検証の正本手順（worktree からでも本体の env.sh を見つける）
cd "$(dirname "$0")/.." || exit 1
if [ -f scripts/env.sh ]; then . ./scripts/env.sh
else
  common=$(git rev-parse --git-common-dir 2>/dev/null)
  main_root=$(cd "$common/.." 2>/dev/null && pwd)
  if [ -n "$main_root" ] && [ -f "$main_root/scripts/env.sh" ]; then . "$main_root/scripts/env.sh"; fi
fi
if [ -z "$ANDROID_HOME" ] && [ ! -f local.properties ] && [ ! -d toolchain ]; then
  echo "check.sh: Android SDK の環境が見つかりません。この環境では検証できないため、CI（GitHub Actions）を gate にしてください" >&2
  exit 1
fi
./gradlew assembleDebug testDebugUnitTest lintDebug --console=plain -q || exit 1
echo "check.sh: assembleDebug / testDebugUnitTest / lintDebug OK"
