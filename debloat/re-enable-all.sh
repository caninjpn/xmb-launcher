#!/usr/bin/env bash
# Undo everything debloat.sh disabled, in one go.
#
#   adb connect <fire-tv-ip>:5555
#   bash re-enable-all.sh
#
# Nothing here was uninstalled — `pm disable-user` only hides a package from the
# system for the current user, so re-enabling restores it exactly as it was.
set -u
ADB="${ADB:-adb}"

PKGS=(
  # --- ads, tracking, telemetry ---
  com.amazon.ftvads.deeplinking
  com.amazon.hybridadidservice
  com.amazon.tv.acr
  com.amazon.device.metrics
  com.amazon.device.telemetry.emitter
  com.amazon.client.metrics.api
  com.amazon.neo.minerva
  com.amazon.neo.minerva.logcat
  com.amazon.perfc
  com.amazon.perfcollection
  com.amazon.dp.logger
  com.amazon.device.logmanager
  com.amazon.device.crashmanager
  com.amazon.adep
  # --- promo and shopping rows ---
  com.amazon.sneakpeek
  com.amazon.tv.earlyaccess
  com.amazon.shoptv.client
  com.amazon.media.recommendations
  com.amazon.kso.blackbird
  com.amazon.minitv.android.app
  # --- tutorials and demo content ---
  com.amazon.tmm.tutorial
  com.amazon.storm.lightning.tutorial
  com.amazon.firetv.troubleshooting
  com.amazon.tv.democontent.provider
  # --- unused extras ---
  com.amazon.tahoe
  com.amazon.bueller.photos
  com.amazon.gamehub
  com.amazon.smarthomemapviewapp
  com.amazon.hearingaid
  com.amazon.tv.matter
  com.amazon.tv.mattercohost
  com.amazon.a4b.mcc
  # --- Alexa and the voice remote ---
  com.amazon.vizzini
  com.amazon.alexa.datastore.app
  com.amazon.alexa.externalmediaplayer.fireos
  com.amazon.alexadirectivebrokerservice
  com.amazon.alexamediaplayer.runtime.ftv
  com.amazon.tv.alexaalerts
  com.amazon.tv.alexanotifications
  amazon.speech.davs.davcservice
  com.amazon.firetv.androidskillconnectioncapability
)

for p in "${PKGS[@]}"; do
  printf '%-52s ' "$p"
  "$ADB" shell "pm enable --user 0 $p" 2>&1 | tr -d '\r' | tail -1
done

echo
echo "Done. Reboot the TV so the restored services start cleanly:"
echo "  $ADB shell reboot"
