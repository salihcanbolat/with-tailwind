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

# Taps the centre of the first on-screen element whose text or description matches the regex $1.
tap() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  adb pull /sdcard/ui.xml /tmp/ui.xml > /dev/null 2>&1
  local point
  point=$(python3 - "$1" <<'PY'
import re, sys, xml.etree.ElementTree as ET
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

# A tidy, dark status bar (Android's demo mode).
adb shell cmd uimode night yes
adb shell settings put global sysui_demo_allowed 1
demo() { adb shell am broadcast -a com.android.systemui.demo -e command "$@" > /dev/null; }
demo enter
demo clock -e hhmm 1042
demo battery -e level 100 -e plugged false
demo network -e wifi show -e level 4 -e mobile show -e level 4

# -g grants the notification permission up front.
adb install -r -g "$APK"

log "first start"
start
shot 01-ilk-acilis 6

log "place the widget through the launcher's own dialog"
if tap "Widget'ı ana ekrana ekle"; then
  sleep 3
  shot 02-widget-ekle-onayi 1
  tap "(?i)^(add|add automatically|ekle|otomatik olarak ekle)$" || true
fi
sleep 2
# Skips the guide if the launcher could not place the widget.
tap "^Şimdilik geç$" || true
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
tap "^Varlık ekle$" && shot 08-varlik-ekle 4
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

ls -l "$OUT"
