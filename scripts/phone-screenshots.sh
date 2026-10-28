#!/usr/bin/env bash
# Takes screenshots of the phone app on an emulator (.github/workflows/phone-screenshots.yml). Expects the
# debug APK to be built and a backend with test data on the host (the emulator reaches it at 10.0.2.2).
# Usage: scripts/phone-screenshots.sh <apk> <output dir>
set -uo pipefail

APK=$1
OUT=$2
APP=com.damla.app
mkdir -p "$OUT"

log() { echo "== $*"; }

shot() {
  sleep "${2:-3}"
  adb exec-out screencap -p > "$OUT/$1.png" && log "screenshot $1"
}

# Taps the centre of the first on-screen element whose text or description matches the regex $1. Reading
# the screen waits for it to be idle, which a moving drop never is, so it gives up after 25 s.
tap() {
  rm -f /tmp/ui.xml
  timeout 25 adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 || true
  adb pull /sdcard/ui.xml /tmp/ui.xml > /dev/null 2>&1 || return 1
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
  adb shell input tap $point
}

start() { adb shell am start -W -n "$APP/.MainActivity" "$@" > /dev/null; }

# Points on the Pixel 6 screen (1080 x 2400) of controls that sit in fixed places.
PIN_BUTTON="540 2103"
ADD_TO_HOME="844 2254"
PANEL_ADD="979 211"

adb shell cmd uimode night yes

# -g grants the notification permission up front.
adb install -r -g "$APK"

log "first start"
start
shot 01-ilk-acilis 6

log "place the widget through the launcher's own dialog"
adb shell input tap $PIN_BUTTON
shot 02-widget-ekle-onayi 4
adb shell input tap $ADD_TO_HOME
shot 03-panel 8

log "home screen with the widget"
adb shell input keyevent KEYCODE_HOME
shot 04-ana-ekran-widget 6

log "the flow over the home screen"
if tap "^Damla: "; then
  shot 05-akis 3
  if tap ", (yükselişte|düşüşte|sabit|dalgalı|piyasa kapalı)$"; then
    shot 06-detay 3
  fi
  adb shell input keyevent KEYCODE_BACK
  sleep 1
  adb shell input keyevent KEYCODE_BACK
fi

log "screens of the app"
start --es "$APP.ASSET" usdtry
shot 07-varlik-ayarlari 4
adb shell input keyevent KEYCODE_BACK
sleep 1
adb shell input tap $PANEL_ADD
shot 08-varlik-ekle 4
adb shell input keyevent KEYCODE_BACK
start -a android.intent.action.VIEW -d "damla://alarms"
shot 09-alarmlar 3
start --es "$APP.ALARM_ASSET" usdtry
shot 10-yeni-alarm 4
adb shell input keyevent KEYCODE_BACK
start --ei "$APP.WIDGET_ID" 1
shot 11-widget-ayarlari 4
adb shell input keyevent KEYCODE_BACK
start --ez "$APP.OPEN_STATUS_SETTINGS" true
shot 12-ayarlar 3

log "the ongoing notification"
if tap "^Bildirim çubuğunda göster$"; then
  sleep 4
  adb shell cmd statusbar expand-notifications
  shot 13-kalici-bildirim 3
  adb shell cmd statusbar collapse
fi

log "pairing code for a computer"
start --ez "$APP.OPEN_STATUS_SETTINGS" true
sleep 2
adb shell input swipe 540 1800 540 600 300
tap "^Bilgisayarla eşitle$" && shot 14-bilgisayarla-esitle 4

adb logcat -d -t 3000 > "$OUT/../logcat.txt" 2>/dev/null || true
ls -l "$OUT"
