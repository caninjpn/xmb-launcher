#!/usr/bin/env bash
# Press remote keys over ADB, wait, and save a screenshot to state/shots/<name>.png.
# Usage: tools/shot.sh <name> [KEYCODE ...]
#   tools/shot.sh home
#   tools/shot.sh apps KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT
# Protected video (live TV, HDMI in, some streaming apps) comes back black: that is the TV's
# content protection, not a bug.
set -uo pipefail
source "$(dirname "$0")/env.sh"
[ $# -ge 1 ] || { echo "Usage: $0 <name> [KEYCODE ...]" >&2; exit 2; }
name="$1"; shift
adb_connect
mkdir -p "$STATE/shots"
for k in "$@"; do adb_tv shell input keyevent "$k"; sleep 0.6; done
sleep 1.5
out="$STATE/shots/$name.png"
adb_tv exec-out screencap -p > "$out"
echo "$out $(wc -c < "$out") bytes"
focused_app
