#!/usr/bin/env bash
# Prints recent emulator logcat for the given tags only (never dump the full log: it is huge).
# Usage: scripts/logcat.sh <Tag> [Tag2 ...] [-n lines]   (default 150 lines; -c clears the log first)
source "$(dirname "$0")/_common.sh"
lines=150; tags=()
while [ $# -gt 0 ]; do
  case "$1" in -n) lines="$2"; shift 2;; -c) "${ADB[@]}" logcat -c; echo cleared; exit 0;; *) tags+=("$1:V"); shift;; esac
done
[ ${#tags[@]} -eq 0 ] && { echo "usage: scripts/logcat.sh <Tag>... [-n lines]"; exit 2; }
"${ADB[@]}" logcat -d -v time -s "${tags[@]}" AndroidRuntime:E | tr -d '\r' | tail -n "$lines"
