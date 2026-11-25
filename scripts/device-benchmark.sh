#!/usr/bin/env bash
# Run only on an authorized idle Android test device. No root or battery-stat reset.
set -euo pipefail
pkg="${1:-com.xdownload.pro.next}"
activity="${2:-com.xdownload.mobile.MainActivity}"
out="${3:-benchmark-$(date +%Y%m%d-%H%M%S)}"
mkdir -p "$out"
adb get-state
adb shell getprop ro.product.model > "$out/model.txt"
adb shell getprop ro.build.version.release > "$out/android.txt"
adb shell dumpsys package "$pkg" > "$out/package.txt"
adb shell dumpsys battery > "$out/battery-before.txt"
adb shell dumpsys thermalservice > "$out/thermal-before.txt" || true
for n in 1 2 3 4 5; do
 adb shell am force-stop "$pkg"
 adb shell am start -W -n "$pkg/$activity" > "$out/start-$n.txt"
 sleep 3
 adb shell dumpsys meminfo "$pkg" > "$out/memory-$n.txt"
done
adb shell dumpsys battery > "$out/battery-after.txt"
adb shell dumpsys thermalservice > "$out/thermal-after.txt" || true
echo "Startup and memory samples: $out. Battery snapshots are context, not energy consumption measurements."
