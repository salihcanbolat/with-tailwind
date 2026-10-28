#!/usr/bin/env bash
# Takes screenshots of the phone app on an emulator (.github/workflows/phone-screenshots.yml). Expects the
# debug APK to be built and a backend with test data on the host (the emulator reaches it at 10.0.2.2).
# The app's own screens come first; the home-screen widget and the flow last, so a failing launcher
# cannot cost the rest. Usage: scripts/phone-screenshots.sh <apk> <output dir>
set -uo pipefail

APK=$1
OUT=$2
APP=com.damla.app
mkdir -p "$OUT"

# Points on the Pixel 6 screen (1080 x 2400) of controls that sit in fixed places (Android 14).
PIN_BUTTON="540 2103"
SKIP_GUIDE="540 2240"
ADD_TO_HOME="844 2254"
PANEL_ADD="979 211"

log() { echo "== $*"; }

shot() {
  sleep "${2:-3}"
  timeout 30 adb exec-out screencap -p > "$OUT/$1.png" && log "screenshot $1"
}

# Taps the centre of the first on-screen element whose text or description matches the regex $1. Reading
# the screen waits for it to be idle, which a moving drop never is, so it gives up after 25 s.
tap() {
  rm -f /tmp/ui.xml
  timeout 25 adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 || true
  timeout 10 adb pull /sdcard/ui.xml /tmp/ui.xml > /dev/null 2>&1 || return 1
  local point
  point=$(python3 - "$1" <<'PY'
import os, re, sys, xml.etree.ElementTree as ET
if not os.path.exists('/tmp/ui.xml'):
    sys.exit()
pattern = re.compile(sys.argv[1])
for node in ET.parse('/tmp/ui.xml').iter('node'):
    label = node.get('text') or node.get('content-desc') or ''
    if pattern.search(label):
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
PY
)
  if [ -z "$point" ]; then
    log "nothing matches '$1' on screen"
    return 1
  fi
  log "tap '$1' at $point"
  timeout 10 adb shell input tap $point
}

input() { timeout 10 adb shell input "$@"; }
start() { timeout 30 adb shell am start -n "$APP/.MainActivity" "$@" > /dev/null; }
back() {
  input keyevent KEYCODE_BACK
  sleep 1
}

# Everything the device logs, for when something goes wrong.
adb logcat -c
adb logcat -v time > "$OUT/../logcat.txt" 2>&1 &
LOGCAT=$!

adb shell cmd uimode night yes
# -g grants the notification permission up front.
timeout 180 adb install -r -g "$APK"

log "first start"
start
shot 01-ilk-acilis 8

log "the app's screens"
input tap $SKIP_GUIDE
shot 02-panel 8
input tap $PANEL_ADD
shot 03-varlik-ekle 5
back
start --es "$APP.ASSET" usdtry
shot 04-varlik-ayarlari 4
back
start -a android.intent.action.VIEW -d "damla://alarms"
shot 05-alarmlar 3
start --es "$APP.ALARM_ASSET" usdtry
shot 06-yeni-alarm 4
back
start --ei "$APP.WIDGET_ID" 1
shot 07-widget-ayarlari 4
back
start --ez "$APP.OPEN_STATUS_SETTINGS" true
shot 08-ayarlar 3

log "the ongoing notification"
if tap "^Bildirim çubuğunda göster$"; then
  sleep 4
  timeout 10 adb shell cmd statusbar expand-notifications
  shot 09-kalici-bildirim 3
  timeout 10 adb shell cmd statusbar collapse
fi

log "pairing code for a computer"
start --ez "$APP.OPEN_STATUS_SETTINGS" true
sleep 2
input swipe 540 1800 540 600 300
tap "^Bilgisayarla eşitle$" && shot 10-bilgisayarla-esitle 5
back

log "the widget: from the first-start guide again, through the launcher's own dialog"
timeout 30 adb shell pm clear "$APP" > /dev/null
timeout 10 adb shell pm grant "$APP" android.permission.POST_NOTIFICATIONS
start
sleep 6
input tap $PIN_BUTTON
shot 11-widget-ekle-onayi 4
tap "^(Add to home screen|Add automatically|ADD AUTOMATICALLY)$" || input tap $ADD_TO_HOME
sleep 8
input keyevent KEYCODE_HOME
shot 12-ana-ekran-widget 8

log "the flow over the home screen"
if tap "^Damla: "; then
  shot 13-akis 4
  back
fi

kill $LOGCAT 2> /dev/null
ls -l "$OUT"
