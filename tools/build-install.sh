#!/usr/bin/env bash
# Build Ember XMB, install it on the TV, make it HOME and check that it really is.
#
# Usage: tools/build-install.sh                 release build (signed if keystore.properties exists)
#        tools/build-install.sh --debug         debug build, bigger and debuggable
#        tools/build-install.sh --build-only    build only, don't touch the TV
#        tools/build-install.sh --reinstall     uninstall first (see "signature" below)
#        GRADLE_FLAGS=--daemon tools/build-install.sh    faster repeat builds
#
# Signature: Android refuses to replace an installed app with one signed by a different key. Going
# from a debug build to a signed release build (or between machines) therefore needs an uninstall,
# which WIPES the launcher's saved state -- favourites, category assignments, row order, theme.
# This script detects that case and tells you; --reinstall does the uninstall for you.
#
# Needs JDK 17+ and an Android SDK with platform 37. JAVA_HOME / ANDROID_HOME are used when set;
# Android Studio's bundled JDK and SDK are found automatically in the usual places.
set -uo pipefail

BUILD_ONLY=0; VARIANT=release; REINSTALL=0
while [ $# -gt 0 ]; do
  case "$1" in
    --build-only) BUILD_ONLY=1 ;;
    --debug)      VARIANT=debug ;;
    --release)    VARIANT=release ;;
    --reinstall)  REINSTALL=1 ;;
    -h|--help)    sed -n '2,20p' "$0"; exit 0 ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
  shift
done
[ "$BUILD_ONLY" = 1 ] && NEED_TV=0
source "$(dirname "$0")/env.sh"
set -e

# --- JDK 17+
if [ -z "${JAVA_HOME:-}" ]; then
  for c in "/c/Program Files/Android/Android Studio/jbr" \
           "/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
           "$HOME/.jdks"/jbr* "$HOME/.jdks"/*17* "$HOME/.jdks"/*21*; do
    if [ -x "$c/bin/java" ] || [ -x "$c/bin/java.exe" ]; then export JAVA_HOME="$c"; break; fi
  done
fi
if [ -z "${JAVA_HOME:-}" ] && command -v java >/dev/null 2>&1; then
  echo "note: JAVA_HOME is unset; using the java on PATH."
elif [ -z "${JAVA_HOME:-}" ]; then
  echo "No JDK found. Install JDK 17 or newer (Android Studio ships one), or set JAVA_HOME." >&2
  exit 1
fi
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi

# --- Android SDK with platform 37
if [ -z "${ANDROID_HOME:-}" ]; then
  if [ -n "${ANDROID_SDK_ROOT:-}" ]; then export ANDROID_HOME="$ANDROID_SDK_ROOT"
  else
    for c in "$LOCALAPPDATA/Android/Sdk" "/c/Users/$USER/AppData/Local/Android/Sdk" \
             "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
      [ -d "$c/platforms" ] && { export ANDROID_HOME="$c"; break; }
    done
  fi
fi
if [ -z "${ANDROID_HOME:-}" ] || ! ls -d "$ANDROID_HOME"/platforms/android-37* >/dev/null 2>&1; then
  cat >&2 <<EOF
No Android SDK with platform 37 found (ANDROID_HOME='${ANDROID_HOME:-}').
Install it with Android Studio's SDK Manager, or:
  sdkmanager "platforms;android-37" "build-tools;36.0.0"
then set ANDROID_HOME.
EOF
  exit 1
fi
export ANDROID_SDK_ROOT="$ANDROID_HOME"

# Gradle (a JVM) needs a native path; on Windows that is C:/..., not /c/...
echo "sdk.dir=$(local_path "$ANDROID_HOME")" > "$ROOT/local.properties"

if [ "$VARIANT" = release ] && [ ! -f "$ROOT/keystore.properties" ]; then
  echo "note: no keystore.properties, so the release APK will be UNSIGNED and will not install."
  echo "      Copy keystore.properties.example and fill it in, or build with --debug."
fi

TASK="assemble$(printf '%s' "${VARIANT:0:1}" | tr '[:lower:]' '[:upper:]')${VARIANT:1}"
APK="$ROOT/app/build/outputs/apk/$VARIANT/app-$VARIANT.apk"
(cd "$ROOT" && sh ./gradlew ${GRADLE_FLAGS:---no-daemon} -q "$TASK")
[ -f "$APK" ] || { echo "Build reported success but $APK is missing." >&2; exit 1; }
echo "built $APK ($(wc -c < "$APK") bytes, $VARIANT)"
if [ "$BUILD_ONLY" = 1 ]; then exit 0; fi

# --- Install. push + pm install rather than "adb install": plain adb install can hang on newer
# Android waiting for a confirmation nobody is there to give.
adb_connect
if [ "$REINSTALL" = 1 ]; then
  echo "uninstall $PKG -> $(tv_sh pm uninstall "$PKG" || true)"
fi
adb_tv push "$(local_path "$APK")" /data/local/tmp/xmb.apk >/dev/null
# `|| true`: a failed install must reach the guidance below, not trip `set -e` first.
result="$(tv_sh pm install -r /data/local/tmp/xmb.apk || true)"
echo "pm install: $result"
adb_tv shell rm -f /data/local/tmp/xmb.apk
if [[ "$result" != *Success* ]]; then
  echo >&2
  case "$result" in
    *UPDATE_INCOMPATIBLE*|*signature*|*SIGNATURE*)
      cat >&2 <<'EOF'
The installed copy was signed with a different key, and Android will not replace an app in
place with one signed by another key. This is what going from a debug build to a signed
release build looks like (and from one machine's keystore to another's).
EOF
      ;;
    *VERSION_DOWNGRADE*)
      cat >&2 <<'EOF'
The installed copy has a HIGHER versionCode than the one you just built, and Android will not
install backwards. A debug build sits at the versionCode in app/build.gradle.kts, so this is
what you get when a newer release build is already on the box.
EOF
      ;;
    *INSUFFICIENT_STORAGE*)
      echo "The TV is out of space. Uninstall something, or: adb shell pm trim-caches 500M" >&2 ;;
    *)
      echo "Install failed. The full message from the TV is above." >&2 ;;
  esac
  cat >&2 <<'EOF'

Removing the installed copy first gets past all of these:

  tools/build-install.sh --reinstall

That WIPES the launcher's saved state -- favourites, category assignments, row order, theme.
tools/backup.sh keeps a copy of those prefs when a debuggable build is installed.
EOF
  exit 1
fi

# Usage access powers the recents row and Continue Watching. It is a special permission, so it is
# an appop rather than a pm grant -- and on Fire TV the Settings screen for it is a stub, which is
# exactly why setting it over ADB is worth doing.
echo "usage access -> $(tv_sh appops set "$PKG" GET_USAGE_STATS allow >/dev/null 2>&1; tv_sh appops get "$PKG" GET_USAGE_STATS || true)"

echo "set-home-activity: $(tv_sh cmd package set-home-activity "$HOME_ACTIVITY" || true)"
adb_tv shell input keyevent KEYCODE_HOME
sleep 2
focused="$(focused_app)"
echo "$focused"
if [[ "$focused" == *"$PKG"* ]]; then
  echo "Ember XMB is HOME ($PKG)."
  exit 0
fi

if is_fire_tv; then
  cat >&2 <<'EOF'

HOME still opens Amazon's launcher, and on Fire TV it always will. Fire OS gives its own
launcher an intent-filter priority of 950; Android forces every non-privileged package to 0,
so no sideloaded app can outbid it. set-home-activity prints Success and changes nothing.
Launch Ember XMB from Settings > Applications, or bind it to a remote button with an app
like Button Mapper. See README.md, "Why the Home button cannot be claimed on Fire TV".
EOF
  exit 3
fi
cat >&2 <<'EOF'

WARNING: HOME still opens another app. On Google TV, set-home-activity can print Success and
change nothing while the stock launcher is enabled. Disable it and try again:
  tools/disable.sh stock-home com.google.android.apps.tv.launcherx
  tools/build-install.sh
EOF
exit 3
