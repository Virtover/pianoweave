#!/usr/bin/env bash
# Text view of the emulator screen (texts, content descriptions, centers), far cheaper than a screenshot.
# Use it to check what is shown and where to tap (adb shell input tap X Y). Usage: scripts/ui.sh
source "$(dirname "$0")/_common.sh"
"${ADB[@]}" shell uiautomator dump /sdcard/ui.xml >/dev/null || exit 1
"${ADB[@]}" exec-out cat /sdcard/ui.xml | tr -d '\r' | sed 's/<node /\n<node /g' | awk '
  /^<node / {
    t = d = ""
    if (match($0, / text="[^"]*"/)) t = substr($0, RSTART + 7, RLENGTH - 8)
    if (match($0, / content-desc="[^"]*"/)) d = substr($0, RSTART + 15, RLENGTH - 16)
    if (t d == "") next
    match($0, /bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"/)
    split(substr($0, RSTART + 9, RLENGTH - 11), b, /[^0-9]+/)
    out = "(" int((b[1] + b[3]) / 2) "," int((b[2] + b[4]) / 2) ")"
    if ($0 ~ /clickable="true"/) out = out " [tap]"
    if (t != "") out = out " \"" t "\""
    if (d != "" && d != t) out = out " desc=\"" d "\""
    gsub(/&#10;/, " ", out); gsub(/&quot;/, "\"", out); gsub(/&apos;|&#39;/, "\047", out)
    gsub(/&lt;/, "<", out); gsub(/&gt;/, ">", out); gsub(/&amp;/, "\\&", out)
    print out
  }'
