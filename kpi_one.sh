#!/usr/bin/env bash
# KPI "one-liner" per la demo.
# Combina:
#  - Stato & processingTime dal topic robot
#  - totalProcessed / goodCount / badCount dal topic quality sensor
#  - active / speed dal topic conveyor (se richiesto)
#  - Throughput ~ ΔtotalProcessed / Δt usando due messaggi consecutivi
#  - Yield = good/total ; Scrap = bad/total
#
# Uso:
#   ./kpi_one.sh           # robot + quality (default)
#   ./kpi_one.sh --with-conveyor   # include active/speed
#   Variabili opzionali:
#     MQTT_HOST, CELL, ROBOT, QUALITY_SENSOR, CONVEYOR

# KPI one-liner (robot+quality, opzionale conveyor)
set -euo pipefail

MQTT_SUB="mosquitto_sub"; JQ="jq"; BC="bc"
need(){ command -v "$1" >/dev/null 2>&1 || { echo "Manca $1" >&2; exit 1; }; }
need "$MQTT_SUB"; need "$JQ"; need "$BC"

MQTT_HOST="${MQTT_HOST:-localhost}"
CELL="${CELL:-cell-01}"
ROBOT="${ROBOT:-robot-001}"
QUALITY_SENSOR="${QUALITY_SENSOR:-sensor-qs-001}"
CONVEYOR="${CONVEYOR:-conveyor-001}"
WITH_CONVEYOR="${1:-}"

ROBOT_T="mf/${CELL}/robot/${ROBOT}/status"
QUAL_T="mf/${CELL}/quality/${QUALITY_SENSOR}/status"
CONV_T="mf/${CELL}/conveyor/${CONVEYOR}/status"

read_one(){
  # Legge 1 messaggio JSON e lo ripulisce dai CR; fallisce se non è JSON
  local topic="$1"
  local line
  line="$("$MQTT_SUB" -h "$MQTT_HOST" -t "$topic" -C 1 -W 5 || true)"
  line="$(printf '%s' "$line" | tr -d '\r')"
  printf '%s' "$line" | $JQ -e . >/dev/null 2>&1 || return 1
  printf '%s' "$line"
}

# ---- due snapshot dal quality per throughput ----
Q1="$(read_one "$QUAL_T")" || { echo "Errore: nessun JSON valido dal topic $QUAL_T (1/2)"; exit 1; }
Q2="$(read_one "$QUAL_T")" || { echo "Errore: nessun JSON valido dal topic $QUAL_T (2/2)"; exit 1; }

t1=$($JQ -r '.timestamp'      <<<"$Q1");  t2=$($JQ -r '.timestamp'      <<<"$Q2")
tp1=$($JQ -r '.totalProcessed'<<<"$Q1");  tp2=$($JQ -r '.totalProcessed'<<<"$Q2")
g2=$($JQ -r '.goodCount'      <<<"$Q2");  b2=$($JQ -r '.badCount'       <<<"$Q2")

# Δt (ms->s) e Δ pezzi
dt=$(echo "scale=3; ($t2-$t1)/1000" | $BC)
dproc=$(echo "$tp2-$tp1" | $BC)

thr_min=$(echo "scale=2; if ($dt>0) ($dproc/$dt)*60 else 0" | $BC)
yield=$(echo "scale=4; if ($tp2>0) $g2/$tp2 else 0" | $BC)
scrap=$(echo "scale=4; if ($tp2>0) $b2/$tp2 else 0" | $BC)

# ---- stato robot (+ processingTime) ----
R="$(read_one "$ROBOT_T" || echo '{}')"
state=$(echo "$R" | $JQ -r '.status // "UNKNOWN"')
procTime=$(echo "$R" | $JQ -r '.processingTime // 0')

# ---- conveyor opzionale ----
conv_str=""
if [[ "$WITH_CONVEYOR" == "--with-conveyor" ]]; then
  C="$(read_one "$CONV_T" || echo '{}')"
  active=$(echo "$C" | $JQ -r '.active // false')
  speed=$(echo "$C" | $JQ -r '.speed // 0')
  conv_str="  convActive=${active}  convSpeed=${speed}"
fi

printf "state=%s  procTime=%ss  total=%s  good=%s  bad=%s  yield=%.2f  scrap=%.2f  ~throughput=%.2f pz/min%s\n" \
  "$state" "$procTime" "$tp2" "$g2" "$b2" "$yield" "$scrap" "$thr_min" "$conv_str"
