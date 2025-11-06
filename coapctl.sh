#!/usr/bin/env bash
# Controller per la Microfactory:
#  - Ping / Devices via CoAP
#  - Comandi START/STOP/RESET via CoAP
#  - Stato (IDLE/PROCESSING/ALARM) via MQTT
#
# Dipendenze: coap-client-openssl, mosquitto-clients, jq (uuidgen facoltativo)
# Uso:
#   ./coapctl.sh ping
#   ./coapctl.sh devices
#   ./coapctl.sh start|stop|reset
#   ./coapctl.sh status
# Variabili (opzionali):
#   COAP_HOST=localhost COAP_PORT=5683 MQTT_HOST=localhost \
#   CELL=cell-01 ROBOT=robot-001 ACK_TIMEOUT=8 ACK_WINDOW_MS=10000 DEBUG=1 ./coapctl.sh start

set -euo pipefail

# --- Config di default (override con variabili d'ambiente) ---
COAP_HOST="${COAP_HOST:-localhost}"
COAP_PORT="${COAP_PORT:-5683}"
MQTT_HOST="${MQTT_HOST:-localhost}"
CELL="${CELL:-cell-01}"
ROBOT="${ROBOT:-robot-001}"
QUALITY_SENSOR="${QUALITY_SENSOR:-sensor-qs-001}"

COAP="${COAP:-/usr/bin/coap-client-openssl}"
MQTT_SUB="${MQTT_SUB:-mosquitto_sub}"
MQTT_PUB="${MQTT_PUB:-mosquitto_pub}"

BASE="coap://${COAP_HOST}:${COAP_PORT}"
ROBOT_BASE="/factory/${CELL}/robot/${ROBOT}"
CMD_PATH="${ROBOT_BASE}/cmd"
ROBOT_STATUS_TOPIC="mf/${CELL}/robot/${ROBOT}/status"
ACK_TOPIC="mf/${CELL}/robot/${ROBOT}/ack"

ACK_TIMEOUT="${ACK_TIMEOUT:-8}"           # secondi
ACK_WINDOW_MS="${ACK_WINDOW_MS:-10000}"   # ms (match alternativo su ts)
DEBUG="${DEBUG:-0}"

die(){ echo "Errore: $*" >&2; exit 1; }
dbg(){ [ "$DEBUG" = "1" ] && echo "[DBG] $*" >&2 || true; }

need() { command -v "$1" >/dev/null 2>&1 || die "Comando richiesto non trovato: $1"; }
need "$COAP"; need "$MQTT_SUB"; need jq

ping_coap() {
  echo "• .well-known/core:"
  "$COAP" -m get "${BASE}/.well-known/core"
}

devices_coap() {
  echo "• GET /factory/${CELL}/devices"
  "$COAP" -m get -A application/json "${BASE}/factory/${CELL}/devices"
}

status_mqtt() {
  echo "• Stato robot da MQTT (${ROBOT_STATUS_TOPIC})"
  "$MQTT_SUB" -h "$MQTT_HOST" -t "$ROBOT_STATUS_TOPIC" -C 1 \
    | jq -r '"state=\(.status) procTime=\(.processingTime)s"'
}

gen_msgid() {
  if command -v uuidgen >/dev/null 2>&1; then uuidgen; else echo "m$(date +%s%3N)-$RANDOM"; fi
}

# --- Sottoscrizione anticipata al topic ACK per evitare race ---
# Crea una FIFO, parte mosquitto_sub in background e ci scrive dentro.
SUB_PID=""
SUB_FIFO=""
start_ack_sub() {
  SUB_FIFO="$(mktemp -u)"
  mkfifo "$SUB_FIFO"
  # -q : quiet banner; niente -v (non ci serve il topic)
  # NB: niente -W/-C qui: gestiamo noi il timeout.
  "$MQTT_SUB" -h "$MQTT_HOST" -t "$ACK_TOPIC" > "$SUB_FIFO" 2>/dev/null &
  SUB_PID=$!
  dbg "Subscriber avviato pid=$SUB_PID fifo=$SUB_FIFO"
  # piccolo sleep per assicurare la sottoscrizione prima del POST
  sleep 0.15
}

stop_ack_sub() {
  if [ -n "${SUB_PID:-}" ] && kill -0 "$SUB_PID" 2>/dev/null; then
    kill "$SUB_PID" 2>/dev/null || true
    wait "$SUB_PID" 2>/dev/null || true
  fi
  if [ -n "${SUB_FIFO:-}" ] && [ -p "$SUB_FIFO" ]; then rm -f "$SUB_FIFO"; fi
  SUB_PID=""; SUB_FIFO=""
}

# Legge dalla FIFO fino a timeout e restituisce il JSON dell'ACK se matcha per msgId o ts-window
wait_ack_from_fifo() {
  local typ="$1"; local since="$2"; local mid="$3"
  local deadline=$(( $(date +%s) + ACK_TIMEOUT ))
  local line candidate

  while true; do
    # interrompi se timeout
    if [ "$(date +%s)" -ge "$deadline" ]; then
      dbg "Timeout in attesa di ACK"
      echo ""
      return 0
    fi
    # read con timeout breve per poter controllare il deadline
    if IFS= read -r -t 0.5 line < "$SUB_FIFO"; then
      dbg "RX: $line"
      candidate=$(printf '%s' "$line" | jq -rc \
        --arg typ "$typ" \
        --arg mid "$mid" \
        --argjson since "$since" \
        --argjson win "$ACK_WINDOW_MS" '
          select((.cmdType? // "") == $typ)
          | select(
              (.msgId? // "") == $mid
              or (
                (try (.ts) catch 0) >= $since
                and (try (.ts) catch 0) <= ($since + $win)
              )
            )
        ' 2>/dev/null || true)
      if [ -n "$candidate" ]; then
        echo "$candidate"
        return 0
      fi
    fi
  done
}

send_cmd() {
  local typ="$1"
  local ts msgid payload ack ats

  ts=$(date +%s%3N)
  msgid=$(gen_msgid)
  payload=$(printf '{"type":"%s","ts":%s,"msgId":"%s"}' "$typ" "$ts" "$msgid")

  echo "• POST ${CMD_PATH} payload=${payload}"

  # 1) Subscribe prima del POST per non perdere l'ACK
  start_ack_sub

  # 2) POST CoAP (ignora l'output, non tutti gli endpoint rispondono con payload)
  if ! "$COAP" -m post -t application/json -e "$payload" "${BASE}${CMD_PATH}" >/dev/null 2>&1; then
    dbg "coap-client ha restituito errore (potrebbe essere solo EMPTY ACK CoAP)."
  fi

  echo "• ACK:"
  # 3) Attendi ACK sulla FIFO
  ack=$(wait_ack_from_fifo "$typ" "$ts" "$msgid")

  # 4) Chiudi subscriber e FIFO
  stop_ack_sub

  if [ -n "$ack" ]; then
    printf '%s\n' "$ack" | jq -r '"cmdType=\(.cmdType) status=\(.status) message=\(.message) ts=\(.ts) msgId=\(.msgId // "-")"'
    ats=$(printf '%s' "$ack" | jq -r 'try .ts catch 0' 2>/dev/null || echo 0)
    if [[ "$ats" =~ ^[0-9]+$ ]] && [[ "$ats" -gt 0 ]]; then
      echo "• Latenza ≈ $((ats - ts)) ms"
    fi
  else
    echo "Nessun ACK per cmdType='${typ}' entro ${ACK_TIMEOUT}s (window ${ACK_WINDOW_MS}ms)"
  fi
}

case "${1:-}" in
  ping)   ping_coap || die "Ping fallito" ;;
  devices) devices_coap ;;
  start)  send_cmd START ;;
  stop)   send_cmd STOP ;;
  reset)  send_cmd RESET ;;
  status) status_mqtt ;;
  *)
    cat <<'USAGE'
Uso: ./coapctl.sh {ping|devices|start|stop|reset|status}
(stato via MQTT; comandi e ping via CoAP)
Variabili: COAP_HOST COAP_PORT MQTT_HOST CELL ROBOT QUALITY_SENSOR ACK_TIMEOUT ACK_WINDOW_MS DEBUG
USAGE
    exit 1
    ;;
esac
