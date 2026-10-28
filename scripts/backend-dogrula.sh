#!/usr/bin/env bash
# Runs the backend locally against real prices and prints snapshots, to verify phase 2 on a machine
# with internet access. Needs Docker, JDK 21 and curl.
#
#   scripts/backend-dogrula.sh                 # Binance only (crypto)
#   DAMLA_SOURCES=binance,test scripts/backend-dogrula.sh
#       # Binance first, synthetic test prices as backup: turn Wi-Fi off for a minute and the
#       # "source" field switches to "test", then back to "binance". Use a throwaway database
#       # (docker compose down -v afterwards), because the backup prices are synthetic.
set -euo pipefail
cd "$(dirname "$0")/.."

export DAMLA_SOURCES="${DAMLA_SOURCES:-binance,truncgil}"
export PORT="${PORT:-8080}"
export DATABASE_URL="jdbc:postgresql://localhost:5432/damla"
export DATABASE_USER=damla
export DATABASE_PASSWORD=damla
ASSETS="${ASSETS:-btcusdt,ethusdt}"
LOG=backend/build/dogrula.log

docker compose up -d --wait db
./gradlew -q :backend:installDist
mkdir -p backend/build
backend/build/install/backend/bin/backend >"$LOG" 2>&1 &
SERVER=$!
trap 'kill $SERVER 2>/dev/null || true' EXIT

echo "Sunucu başlıyor; ilk çalıştırmada 31 günlük geçmiş indirilir (bir iki dakika sürebilir). Günlük: $LOG"
until curl -fs "localhost:$PORT/health" >/dev/null; do sleep 1; done
until curl -fs "localhost:$PORT/v1/snapshot?assets=$ASSETS" | grep -q '"drop":{'; do sleep 2; done

pretty() { if command -v python3 >/dev/null; then python3 -m json.tool; else cat; fi; }
for round in 1 2 3; do
  echo "--- Snapshot $round ($(date +%H:%M:%S)) ---"
  curl -fs "localhost:$PORT/v1/snapshot?assets=$ASSETS" | pretty
  [ "$round" -lt 3 ] && sleep 60
done
echo "--- Geçmiş (btcusdt, 1 gün, 24 nokta) ---"
curl -fs "localhost:$PORT/v1/history?asset=btcusdt&range=1d" | pretty
echo "Kaynak değişimleri ve hatalar:"
grep -E "switched|WARN|ERROR" "$LOG" || echo "(yok)"
