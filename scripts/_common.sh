# Shared setup for the scripts in this folder. Always targets the emulator: the owner's phone is often
# attached too, and plain gradle/adb would touch every device.
set -o pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.." || exit 1
export ANDROID_SERIAL="${ANDROID_SERIAL:-emulator-5554}"
export MSYS_NO_PATHCONV=1
ADB=(adb -s "$ANDROID_SERIAL")
