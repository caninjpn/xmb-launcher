#!/usr/bin/env bash
# Snapshot the TV before changing anything: package lists, HOME apps, getprop and all settings,
# into state/backup-<date>-<time>/. restore.sh reads the settings back from the OLDEST backup,
# so the first one you take is the one that matters.
# Usage: tools/backup.sh
set -uo pipefail
source "$(dirname "$0")/env.sh"
adb_connect

dir="$STATE/backup-$(date '+%Y%m%d-%H%M')"
mkdir -p "$dir"
tv_sh pm list packages -f --user 0          > "$dir/packages-all.txt"
tv_sh pm list packages -d --user 0          > "$dir/packages-disabled.txt"
tv_sh pm list packages -s --user 0          > "$dir/packages-system.txt"
tv_sh pm list packages -3 --user 0          > "$dir/packages-third-party.txt"
tv_sh cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME > "$dir/home-activities.txt"
tv_sh cmd package resolve-activity -c android.intent.category.HOME -a android.intent.action.MAIN > "$dir/home-resolved.txt"
tv_sh getprop                               > "$dir/getprop.txt"
for ns in global secure system; do
  tv_sh settings list "$ns"                 > "$dir/settings-$ns.txt"
done
focused_app > "$dir/focused-app.txt"

# The launcher's own saved state, readable only while a debuggable build is installed.
# A release build is not debuggable, so this quietly produces nothing -- that is expected.
tv_sh "run-as $PKG sh -c 'cat /data/data/$PKG/shared_prefs/*.xml'" > "$dir/launcher-prefs.xml" 2>/dev/null || true
[ -s "$dir/launcher-prefs.xml" ] || rm -f "$dir/launcher-prefs.xml"

echo "backup: $dir"
wc -l "$dir"/*.txt | sed "s#$dir/##"
