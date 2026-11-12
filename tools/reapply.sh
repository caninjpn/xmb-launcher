#!/usr/bin/env bash
# After a firmware update, or whenever the old home screen comes back: re-disable everything in
# state/disabled.txt that the update re-enabled, make Ember XMB HOME again, and check it.
# Safe to run any number of times; a second run changes nothing.
#
# Usage: tools/reapply.sh
#
# Skipped on purpose:
#   - lines starting with # in state/disabled.txt
#   - packages on the "# firmware-reenables: a b c" line (the firmware turns them back on at every
#     boot; hide them in the launcher instead of fighting it)
#   - packages no longer installed
set -uo pipefail
source "$(dirname "$0")/env.sh"

LOG="$STATE/disabled.txt"
[ -f "$LOG" ] || { echo "No $LOG: nothing to reapply. (tools/disable.sh writes it.)" >&2; exit 1; }
adb_connect

skip=" $(grep -m1 '^# *firmware-reenables:' "$LOG" | cut -d: -f2- | tr -d '\r' | tr ',' ' ') "
wanted="$(grep -v '^#' "$LOG" | tr -d '\r' | sed '/^[[:space:]]*$/d' | sort -u)"
installed="$(tv_sh pm list packages --user 0 | sed 's/^package://')"
disabled="$(tv_sh pm list packages -d --user 0 | sed 's/^package://')"

redisabled=0; fine=0; skipped=0; failed=0; gone=0
for p in $wanted; do
  if [[ "$skip" == *" $p "* ]]; then skipped=$((skipped + 1)); continue; fi
  if ! printf '%s\n' "$installed" | grep -qx "$p"; then gone=$((gone + 1)); continue; fi
  if printf '%s\n' "$disabled" | grep -qx "$p"; then fine=$((fine + 1)); continue; fi

  case "$p" in
    com.google.android.apps.tv.launcherx)
      echo "note: the update re-enabled the stock Google TV home; it outranks Ember XMB until disabled." ;;
  esac

  out="$(tv_sh pm disable-user --user 0 "$p")"
  if [[ "$out" == *"new state: disabled"* ]]; then
    echo "re-disabled $p"; redisabled=$((redisabled + 1))
  else
    echo "FAILED $p: $out"; failed=$((failed + 1))
  fi
done

home="NOT OK"
if printf '%s\n' "$installed" | grep -qx "$PKG"; then
  # Usage access is revoked by some updates; setting it again is free when it is already allowed.
  tv_sh appops set "$PKG" GET_USAGE_STATS allow >/dev/null 2>&1 || true
  tv_sh cmd package set-home-activity "$HOME_ACTIVITY" >/dev/null
  adb_tv shell input keyevent KEYCODE_HOME
  sleep 2
  focused="$(focused_app)"
  echo "$focused"
  [[ "$focused" == *"$PKG"* ]] && home="OK"
  if [ "$home" != OK ] && is_fire_tv; then
    home="N/A (Fire TV: Home cannot be claimed by a sideloaded launcher)"
  fi
else
  echo "Ember XMB ($PKG) is not installed (factory reset?). Run tools/build-install.sh." >&2
fi

echo
echo "re-disabled $redisabled, already fine $fine, skipped $skipped, not installed $gone, failed $failed"
echo "home: $home"
case "$home" in OK|N/A*) [ "$failed" = 0 ] || exit 1 ;; *) exit 1 ;; esac
