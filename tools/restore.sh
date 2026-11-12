#!/usr/bin/env bash
# Put the TV back the way it was.
#
#   tools/restore.sh                       re-enable every package in state/disabled.txt, hand HOME
#                                          back to the stock launcher, restore changed settings
#   tools/restore.sh pkg1 [pkg2 ...]       re-enable just these packages
#   tools/restore.sh --fire-tv             also re-enable everything in tools/fire-tv-packages.txt,
#                                          whether or not this repo is what disabled it
#   tools/restore.sh --uninstall-launcher  all of the above, then uninstall Ember XMB
#
# Nothing here was ever uninstalled: pm disable-user only hides a package from the current user.
# Settings come back from the OLDEST state/backup-*/ folder when there is one.
set -uo pipefail
source "$(dirname "$0")/env.sh"
adb_connect

uninstall=0; firetv=0
while [ "${1:-}" = "--uninstall-launcher" ] || [ "${1:-}" = "--fire-tv" ]; do
  [ "$1" = "--uninstall-launcher" ] && uninstall=1
  [ "$1" = "--fire-tv" ] && firetv=1
  shift
done
LOG="$STATE/disabled.txt"
explicit=$#

if [ $# -gt 0 ]; then
  pkgs="$*"
elif [ -f "$LOG" ]; then
  pkgs="$(grep -v '^#' "$LOG" | tr -d '\r' | sed '/^[[:space:]]*$/d')"
else
  echo "No $LOG and no packages given: nothing from this repo to re-enable."
  pkgs=""
fi

if [ "$firetv" = 1 ] && [ -f "$TOOLS/fire-tv-packages.txt" ]; then
  pkgs="$pkgs $(grep -v '^[[:space:]]*#' "$TOOLS/fire-tv-packages.txt" | tr -d '\r' | sed '/^[[:space:]]*$/d')"
fi

for p in $(printf '%s\n' $pkgs | sort -u); do
  echo "$p -> $(tv_sh pm enable --user 0 "$p")"
done
[ "$explicit" -gt 0 ] && exit 0

# --- HOME back to the stock launcher, whichever one this TV has.
homes="$(tv_sh cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME \
  | grep '/' | sed 's/^[[:space:]]*//')"
stock=""
for preferred in com.amazon.tv.launcher com.google.android.apps.tv.launcherx com.google.android.tvlauncher \
                 com.google.android.leanbacklauncher tv.widgetv.home; do
  stock="$(printf '%s\n' "$homes" | grep "^$preferred/" | head -1)"
  [ -n "$stock" ] && break
done
[ -n "$stock" ] || stock="$(printf '%s\n' "$homes" | grep -v "^$PKG/" | grep -viE 'fallbackhome|setupwraith|setupwizard' | head -1)"
if [ -n "$stock" ]; then
  echo "home -> $stock: $(tv_sh cmd package set-home-activity "$stock")"
else
  echo "No stock launcher found among HOME apps:" >&2
  printf '%s\n' "$homes" >&2
fi

# --- Settings from the first backup, if there is one.
backup="$(ls -d "$STATE"/backup-*/ 2>/dev/null | head -1)"
get_setting() { [ -n "$backup" ] && [ -f "$backup/settings-$1.txt" ] && grep -m1 "^$2=" "$backup/settings-$1.txt" | cut -d= -f2- | tr -d '\r'; }
restore_setting() {
  local v; v="$(get_setting "$1" "$2")"
  if [ -n "$v" ] && [ "$v" != "null" ]; then tv_sh settings put "$1" "$2" "$v" >/dev/null; echo "settings $1 $2 = $v"
  else tv_sh settings delete "$1" "$2" >/dev/null; echo "settings $1 $2 deleted (default)"; fi
}
if [ -n "$backup" ]; then
  echo "restoring settings from $backup"
  restore_setting secure screensaver_enabled
  restore_setting secure screensaver_components
  restore_setting system screen_off_timeout
else
  echo "no backup found: settings left alone (run tools/backup.sh before changing things next time)"
fi

if [ "$uninstall" = 1 ]; then
  echo "uninstall $PKG -> $(tv_sh pm uninstall "$PKG")"
fi

adb_tv shell input keyevent KEYCODE_HOME
sleep 2
focused_app
echo
echo "Reboot the TV so anything you re-enabled starts cleanly:  adb -s $TV shell reboot"
