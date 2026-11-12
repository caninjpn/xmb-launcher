#!/usr/bin/env bash
# Undo everything debloat disabled, in one go.
#
# Fire TV only. Every package in the list is an Amazon one, so this script has nothing to say to a
# generic Android TV box; on one, `pm list packages` is the place to start instead.
#
#   adb connect <fire-tv-ip>:5555
#   bash re-enable-all.sh
#
# The package list lives in tools/fire-tv-packages.txt so that this script, tools/disable.sh and
# tools/restore.sh --fire-tv all read the same one. If you have set up tools/tv.conf, prefer:
#
#   tools/restore.sh --fire-tv
#
# which also hands HOME back to the stock launcher and restores settings from your backup.
#
# Nothing here was uninstalled -- `pm disable-user` only hides a package from the system for the
# current user, so re-enabling restores it exactly as it was.
set -u
ADB="${ADB:-adb}"
LIST="$(cd "$(dirname "$0")/.." && pwd)/tools/fire-tv-packages.txt"

[ -f "$LIST" ] || { echo "Package list not found: $LIST" >&2; exit 1; }

count=0
while read -r p; do
  [ -n "$p" ] || continue
  printf '%-52s ' "$p"
  "$ADB" shell "pm enable --user 0 $p" 2>&1 | tr -d '\r' | tail -1
  count=$((count + 1))
done < <(grep -v '^[[:space:]]*#' "$LIST" | tr -d '\r' | sed '/^[[:space:]]*$/d')

echo
echo "Re-enabled $count packages. Reboot the TV so the restored services start cleanly:"
echo "  $ADB shell reboot"
