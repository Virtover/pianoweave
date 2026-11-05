#!/usr/bin/env bash
# JVM unit tests; prints compile errors, failing tests with a short trace, and a one-line summary.
# Usage: scripts/test.sh [gradle --tests filter, e.g. '*VoiceCommandsTest']
source "$(dirname "$0")/_common.sh"
args=(:app:testDebugUnitTest --console=plain -q)
[ -n "$1" ] && args+=(--tests "$1")
out=$(./gradlew "${args[@]}" 2>&1); status=$?
# Keep a failure line plus its first few trace lines, drop framework frames.
echo "$out" | grep -vE '^\s+at (org\.junit|org\.gradle|java\.|jdk\.|kotlin\.|sun\.|worker\.)' \
  | grep -E -A4 '^e: |FAILED$|What went wrong' | grep -v '^--$' | grep -vE "^> (Run with|Get more help)|^* What went wrong" | head -80
dir=app/build/test-results/testDebugUnitTest
if ls "$dir"/*.xml >/dev/null 2>&1; then
  sum() { grep -ho "$1=\"[0-9]*\"" "$dir"/*.xml | grep -o '[0-9]*' | awk '{s+=$1} END {print s+0}'; }
  echo "tests=$(sum tests) failures=$(sum failures) errors=$(sum errors) skipped=$(sum skipped)"
fi
[ $status -eq 0 ] && echo "OK" || echo "FAILED"
exit $status
