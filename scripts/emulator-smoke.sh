#!/usr/bin/env bash
# Installs the release APK on a running emulator, opens every view, captures
# screenshots and fails if the app crashes.
set -u
APK=dist/Tasker.apk
OUT=dist/screens
PKG=com.tasker.app
mkdir -p "$OUT"
adb wait-for-device
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell wm size 720x1560
adb shell wm density 320
adb install -r -g "$APK"
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
adb logcat -c

shot() {
  sleep 3
  adb exec-out screencap -p > "$OUT/$1.png"
}
open_route() {
  adb shell am start -W -n $PKG/.MainActivity --es route "$1" ${2:-} > /dev/null
  shot "$1${3:-}"
}

adb shell am start -W -n $PKG/.MainActivity > /dev/null
sleep 6
shot 00-launch
for r in today inbox all daily three weekly teuxdeux calendar kanban eisenhower gtd logbook settings sync theme manage; do
  open_route "$r"
done
# Real drag & drop gestures (long-press then move)
open_route eisenhower "" -before-drag
adb shell input draganddrop 180 1000 180 520 2500
shot eisenhower-after-drag
open_route today "" -before-drag
adb shell input draganddrop 300 900 300 570 2500
shot today-after-drag
# Natural-language quick add preview
adb shell am start -W -a android.intent.action.SEND -t text/plain \
  --es android.intent.extra.TEXT "'Call Anna tomorrow 5pm #home +Work !1 * remind me 30m before'" -n $PKG/.MainActivity > /dev/null
shot quickadd
adb shell input keyevent KEYCODE_BACK
adb shell input keyevent KEYCODE_BACK
# Dark themes and RTL
open_route today "--es theme midnight" -midnight
open_route kanban "--es theme dracula" -dracula
open_route teuxdeux "--es theme amoled" -amoled
open_route today "--es theme paper --ez rtl true" -rtl
open_route weekly "" -rtl
adb shell am start -W -n $PKG/.MainActivity --ez rtl false > /dev/null
# Open the sidebar via swipe from the start edge
adb shell input swipe 5 800 600 800 300
shot sidebar

# Tablet layout (landscape, ~1066dp wide): permanent sidebar
adb shell input keyevent KEYCODE_BACK
adb shell am force-stop $PKG
adb shell wm size 1600x1000
adb shell wm density 240
sleep 2
adb shell am start -W -n $PKG/.MainActivity > /dev/null
sleep 4
open_route today "" -tablet
open_route calendar "" -tablet
open_route kanban "" -tablet
adb shell wm size 720x1560
adb shell wm density 320
sleep 3
adb shell wm size 1600x1000
adb shell wm density 240
open_route weekly "" -tablet-resized
adb shell wm size reset
adb shell wm density reset

adb logcat -d > "$OUT/logcat.txt"
if grep -E "FATAL EXCEPTION|AndroidRuntime: Process: $PKG" "$OUT/logcat.txt" > "$OUT/crash.txt"; then
  grep -A 40 "FATAL EXCEPTION" "$OUT/logcat.txt" >> "$OUT/crash.txt"
  echo "App crashed"; cat "$OUT/crash.txt"
  exit 1
fi
rm -f "$OUT/crash.txt"
grep -E "$PKG|TaskerApp" "$OUT/logcat.txt" | grep -E " E |Exception" | head -100 > "$OUT/errors.txt" || true
echo "Smoke test passed"
