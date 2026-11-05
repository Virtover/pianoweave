#!/usr/bin/env bash
# Compile check that prints only errors (and warnings with -w), not Gradle's progress output.
# Usage: scripts/check.sh [-w]
source "$(dirname "$0")/_common.sh"
pattern='^e: |^FAILURE|What went wrong|^> '
[ "$1" = "-w" ] && pattern="$pattern|^w: "
out=$(./gradlew :app:compileDebugKotlin --console=plain -q 2>&1); status=$?
echo "$out" | grep -E "$pattern" | sed -E 's#file:///?[^ ]*/app/src/main/java/com/lumenchord/pianoweave/#…/#' | grep -vE "^> (Run with|Get more help)|^* What went wrong" | head -60
warnings=$(echo "$out" | grep -c '^w: ')
[ $status -eq 0 ] && echo "OK (compiled, $warnings warnings)" || echo "FAILED"
exit $status
