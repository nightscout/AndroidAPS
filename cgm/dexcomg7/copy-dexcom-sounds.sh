#!/bin/sh
# Copies the Dexcom alarm sounds into res/raw with the names the alarm code uses.
# They are Dexcom's files and are not kept in git (see .gitignore); the build needs them.
#
# Usage: ./copy-dexcom-sounds.sh "<folder with the sorted Dexcom sounds>"
# The folder holds "1. Urgent Low", "2. Low", "3. High", "4. Rising:Falling", "5. System", "6. Sounds".
set -e
S="$1"
[ -d "$S" ] || { echo "Usage: $0 <dexcom sounds folder>"; exit 1; }
D="$(dirname "$0")/src/main/res/raw"
mkdir -p "$D"
c() { cp "$S/$1" "$D/$2.m4a"; }
for lv in soft medium intense; do
  c "1. Urgent Low/${lv}_urgent_low.mp4" g7_urgent_low_$lv
  c "2. Low/${lv}_low.mp4" g7_low_$lv
  c "3. High/${lv}_high.mp4" g7_high_$lv
  c "4. Rising:Falling/Falling Rate/${lv}_falling_rate.mp4" g7_fall_rate_$lv
  c "4. Rising:Falling/Rising Rate/${lv}_rising_rate.mp4" g7_rise_rate_$lv
  c "4. Rising:Falling/Urgent Low Soon/${lv}_urgent_low_soon.mp4" g7_urgent_low_soon_$lv
  c "5. System/Brief Sensor Issue/${lv}_bsi.mp4" g7_sensor_issue_$lv
  c "5. System/System/${lv}_system_alert.mp4" g7_system_$lv
  c "5. System/Technical/${lv}_technical_alerts.mp4" g7_technical_$lv
done
c "1. Urgent Low/Alert Style/urgent_low.mp4" g7_urgent_low_classic
c "1. Urgent Low/Alert Style/urgent_low_alarm.mp4" g7_urgent_low_classic_2
c "2. Low/Alert Style/low.mp4" g7_low_classic
c "2. Low/Alert Style/low_alert.mp4" g7_low_classic_2
c "3. High/Alert Style/high.mp4" g7_high_classic
c "3. High/Alert Style/high_alert.mp4" g7_high_classic_2
c "4. Rising:Falling/Falling Rate/Alert Style/fall_rate.mp4" g7_fall_rate_classic
c "4. Rising:Falling/Rising Rate/Alert Style/rise_rate.mp4" g7_rise_rate_classic
c "4. Rising:Falling/Urgent Low Soon/Alert Style/urgent_low_soon.mp4" g7_urgent_low_soon_classic
c "5. System/Signal Loss/Alert Style/signal_loss_alert.mp4" g7_signal_loss_classic
for f in "$S/6. Sounds/"*.mp4; do cp "$f" "$D/g7_extra_$(basename "$f" .mp4).m4a"; done
echo "Copied $(ls "$D"/g7_*.m4a | wc -l | tr -d ' ') sounds to $D"
