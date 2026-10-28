#!/usr/bin/env bash
# Takes screenshots of the phone app on an emulator (.github/workflows/phone-screenshots.yml). Expects the
# debug APK to be built and a backend with test data on the host (the emulator reaches it at 10.0.2.2).
# The home screen comes first: the widget as the first-start guide places it, the flow and a detail it
# opens, the widget wide and boxed; then a few of the app's screens. Usage:
# scripts/phone-screenshots.sh <apk> <output dir>
set -uo pipefail

APK=$1
OUT=$2
APP=com.damla.app
mkdir -p "$OUT"

# Points on the Pixel 6 screen (1080 x 2400) of controls that sit in fixed places (Android 14).
PIN_BUTTON="540 2103"
ADD_TO_HOME="844 2254"
PANEL_ADD="979 211"

# The end of a drop's description in the flow ("Altın, yükselişte").
STATES="(yükselişte|düşüşte|sabit|dalgalı|piyasa kapalı)"

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

# Prints "x1 y1 x2 y2" of the widget on the home screen.
widget_bounds() {
  rm -f /tmp/ui.xml
  timeout 25 adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 || true
  timeout 10 adb pull /sdcard/ui.xml /tmp/ui.xml > /dev/null 2>&1 || return 1
  python3 - <<'PY'
import re, sys, xml.etree.ElementTree as ET
for node in ET.parse('/tmp/ui.xml').iter('node'):
    if (node.get('content-desc') or '').startswith('Damla: '):
        print(*re.findall(r'\d+', node.get('bounds')))
        sys.exit()
sys.exit(1)
PY
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

# Right after boot the launcher is still loading and drops requests to place a widget.
input keyevent KEYCODE_HOME
sleep 40

log "first start: the guide places the widget through the launcher's own dialog"
start
shot 01-ilk-acilis 8
input tap $PIN_BUTTON
shot 02-widget-ekle-onayi 4
for attempt in 1 2 3; do
  tap "^(Add to home screen|Add automatically|ADD AUTOMATICALLY)$" || input tap $ADD_TO_HOME
  sleep 8
  input keyevent KEYCODE_HOME
  sleep 4
  widget_bounds > /dev/null && break
  log "the widget is not on the home screen (attempt $attempt): the guide again"
  timeout 30 adb shell pm clear "$APP" > /dev/null
  timeout 10 adb shell pm grant "$APP" android.permission.POST_NOTIFICATIONS
  sleep 15
  start
  sleep 8
  input tap $PIN_BUTTON
  sleep 4
done
shot 03-ana-ekran-mini 8

log "the flow and a detail over the home screen"
if tap "^Damla: "; then
  shot 04-akis-mini 5
  tap "^[^:]+, $STATES$" && shot 05-detay 5
  back
  back
fi

log "the widget three cells wide: long press, then drag the resize frame's right handle"
if bounds=$(widget_bounds); then
  read -r x1 y1 x2 y2 <<< "$bounds"
  cy=$(((y1 + y2) / 2))
  input swipe $(((x1 + x2) / 2)) $cy $(((x1 + x2) / 2)) $cy 1500
  sleep 2
  input swipe $((x2 - 4)) $cy $((x2 + 2 * (x2 - x1) + 60)) $cy 1500
  sleep 2
  input keyevent KEYCODE_HOME
  shot 06-ana-ekran-genis 6
  if tap "^Damla: "; then
    shot 07-akis-genis 5
    back
  fi
fi

log "the boxed style, saved in the widget's settings"
start --ei "$APP.WIDGET_ID" 1
sleep 4
tap "^Kutulu$" && sleep 1 && tap "^Kaydet$"
sleep 3
back
input keyevent KEYCODE_HOME
shot 08-ana-ekran-kutulu 8
if tap "^Damla: "; then
  shot 09-akis-kutulu 5
  back
fi

log "the app's screens"
start
shot 10-panel 6
input tap $PANEL_ADD
shot 11-varlik-ekle 5
back
start --es "$APP.ASSET" usdtry
shot 12-varlik-ayarlari 4
back
start -a android.intent.action.VIEW -d "damla://alarms"
shot 13-alarmlar 3
start --ez "$APP.OPEN_STATUS_SETTINGS" true
shot 14-ayarlar 3
input swipe 540 1800 540 600 300
tap "^Bilgisayarla eşitle$" && shot 15-bilgisayarla-esitle 5
back

kill $LOGCAT 2> /dev/null
ls -l "$OUT"
