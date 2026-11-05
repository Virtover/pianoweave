#!/usr/bin/env bash
# Installs app + test APK on the emulator, runs one instrumented test class, prints a short result.
# Usage: scripts/itest.sh <TestClass or full.class.Name[#method]> [extra am instrument args, e.g. -e model big]
# --no-install skips the Gradle install step (APKs already current).
source "$(dirname "$0")/_common.sh"
install=1; [ "$1" = "--no-install" ] && { install=0; shift; }
cls="$1"; shift
[ -z "$cls" ] && { echo "usage: scripts/itest.sh [--no-install] <TestClass> [am args]"; exit 2; }
if [[ "$cls" != *.* ]]; then
  file=$(git ls-files "app/src/androidTest/*/$cls.kt" "app/src/androidTest/*/$cls.java" | head -1)
  [ -z "$file" ] && { echo "test class $cls not found"; exit 2; }
  cls=$(echo "$file" | sed -E 's#app/src/androidTest/(java|kotlin)/##; s#\.(kt|java)$##; s#/#.#g')
fi
if [ $install -eq 1 ]; then
  out=$(./gradlew :app:installDebug :app:installDebugAndroidTest --console=plain -q 2>&1) \
    || { echo "$out" | grep -E '^e: |What went wrong|^> ' | head -30; echo "INSTALL FAILED"; exit 1; }
fi
"${ADB[@]}" shell am instrument -w -e class "$cls" "$@" \
  com.lumenchord.pianoweave.test/androidx.test.runner.AndroidJUnitRunner 2>&1 \
  | tr -d '\r' | grep -vE '^\s+at (androidx\.test|org\.junit|java\.|android\.app\.Instrumentation|kotlin\.)' \
  | grep -vE '^\.*$|^INSTRUMENTATION_STATUS' | grep -vE "^> (Run with|Get more help)|^* What went wrong" | head -100
