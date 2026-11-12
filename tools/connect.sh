#!/usr/bin/env bash
# Choose how this computer talks to the TV, test it, and remember it in tools/tv.conf (gitignored).
# Every other script reads tools/tv.conf, so you do this once per TV (or when you switch).
#
# Usage: tools/connect.sh                    ask: USB cable or Wi-Fi?
#        tools/connect.sh usb [serial]       USB cable (serial only needed with 2+ USB devices)
#        tools/connect.sh wifi <ip> [port]   Wi-Fi / network (port defaults to 5555)
#
#   USB   - nothing listens on the network, and it survives Wi-Fi changes. Needs a debug-capable
#           port: many TVs' USB-A ports are host-only (for drives) and never work.
#   Wi-Fi - works with every Android TV, no cable, but port 5555 is open and unencrypted on your
#           network until you turn debugging off again.
set -uo pipefail
NEED_TV=0
source "$(dirname "$0")/env.sh"

[ -n "${ADB_BIN:-}" ] || {
  echo "adb not found. Put it on your PATH or set ANDROID_HOME." >&2
  exit 1
}

mode="${1:-}"
mode="$(printf '%s' "$mode" | tr '[:upper:]' '[:lower:]')"
case "$mode" in wi-fi|network|tcp) mode=wifi ;; cable) mode=usb ;; esac
_prev_ip="${TV_IP:-}"
interactive=0
pin_serial=0

if [ -z "$mode" ]; then
  interactive=1
  cur="${TV_CONNECTION:-none}"
  cat <<EOF
How should this computer connect to the TV?   (currently: $cur)

  1) USB cable  - TV plugged into this computer, USB debugging on. Private, no IP needed.
  2) Wi-Fi      - TV and computer on the same network, ADB debugging on. Needs the TV's IP.

EOF
  while :; do
    printf 'Choose 1 (USB) or 2 (Wi-Fi): '
    read -r answer || exit 1
    case "$(printf '%s' "$answer" | tr '[:upper:]' '[:lower:]')" in
      1|usb|u) mode=usb; break ;;
      2|wifi|wi-fi|w) mode=wifi; break ;;
      *) echo "Please type 1 or 2." ;;
    esac
  done
fi

case "$mode" in
  usb)
    TV_CONNECTION=usb
    TV_SERIAL="${2:-}"
    [ -n "$TV_SERIAL" ] && pin_serial=1
    TV_IP=""; TV_PORT=5555
    if [ "$interactive" = 1 ]; then
      echo
      echo "Plug the TV in and turn on USB debugging. Watch the TV for 'Allow USB debugging?'"
      echo "and accept it (tick 'Always allow')."
      printf 'Press Enter when ready... '; read -r _ || true
      list="$(usb_devices || true)"
      n="$(printf '%s\n' "$list" | grep -c . || true)"
      if [ "${n:-0}" -gt 1 ]; then
        echo "USB devices found:"
        printf '%s\n' "$list" | awk -F'\t' '{ printf "  %d) %s (%s)\n", NR, $1, $2 }'
        printf 'Which one is the TV? '
        read -r pick || exit 1
        TV_SERIAL="$(printf '%s\n' "$list" | sed -n "${pick}p" | cut -f1)"
        [ -n "$TV_SERIAL" ] || { echo "No such device." >&2; exit 1; }
        pin_serial=1
      fi
    fi
    TV="${TV_SERIAL:-}"
    adb_connect
    ;;
  wifi)
    TV_CONNECTION=wifi
    TV_SERIAL=""
    TV_IP="${2:-}"
    TV_PORT="${3:-5555}"
    if [ -z "$TV_IP" ]; then
      echo
      echo "Find the TV's IP: Settings > Network & Internet > (your network)."
      echo "Turn on ADB debugging in Settings > System > Developer options."
      printf 'TV IP address%s: ' "${_prev_ip:+ [$_prev_ip]}"
      read -r TV_IP || exit 1
      [ -n "$TV_IP" ] || TV_IP="$_prev_ip"
    fi
    [ -n "$TV_IP" ] || { echo "No IP address given." >&2; exit 1; }
    echo "Connecting to $TV_IP:$TV_PORT; watch the TV for 'Allow USB debugging?' and accept it."
    adb_connect
    ;;
  *)
    echo "Usage: $0 [usb [serial] | wifi <ip> [port]]" >&2
    exit 2 ;;
esac

{
  echo "# Written by tools/connect.sh on $(date '+%F %T'). Rerun it to switch between USB and Wi-Fi."
  echo "TV_CONNECTION=$TV_CONNECTION"
  if [ "$TV_CONNECTION" = usb ]; then
    if [ "$pin_serial" = 1 ]; then echo "TV_SERIAL=$TV_SERIAL"; fi
  else
    echo "TV_IP=$TV_IP"
    [ "$TV_PORT" = 5555 ] || echo "TV_PORT=$TV_PORT"
  fi
} > "$TOOLS/tv.conf"

model="$(tv_sh getprop ro.product.model)"
release="$(tv_sh getprop ro.build.version.release)"
sdk="$(tv_sh getprop ro.build.version.sdk)"
echo "connected over $TV_CONNECTION ($TV): $model, Android $release (API $sdk)"
if is_fire_tv; then
  echo
  echo "This is a Fire TV. The launcher installs and runs, but it CANNOT take the Home"
  echo "button: Fire OS gives its own launcher a priority no sideloaded app can outbid."
  echo "See README.md, 'Why the Home button cannot be claimed on Fire TV'."
fi
echo "saved to tools/tv.conf"
