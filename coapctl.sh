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

# Variabili globali per il subscriber
SUB_PID=""
SUB_FIFO=""
SUB_READY_FLAG=""

# Cleanup function da chiamare sempre
cleanup() {
  if [ -n "${SUB_PID:-}" ] && kill -0 "$SUB_PID" 2>/dev/null; then
    kill "$SUB_PID" 2>/dev/null || true
    wait "$SUB_PID" 2>/dev/null || true
  fi
  [ -n "${SUB_FIFO:-}" ] && [ -p "$SUB_FIFO" ] && rm -f "$SUB_FIFO"
  [ -n "${SUB_READY_FLAG:-}" ] && [ -f "$SUB_READY_FLAG" ] && rm -f "$SUB_READY_FLAG"
}

trap cleanup EXIT INT TERM

# Avvia subscriber con meccanismo di sincronizzazione
start_ack_sub() {
  SUB_FIFO="$(mktemp -u)"
  SUB_READY_FLAG="$(mktemp -u)"
  mkfifo "$SUB_FIFO"

  # Avvia mosquitto_sub che scrive un marker quando è connesso
  (
    # Usa -R per NON ricevere messaggi retained (vecchi)
    "$MQTT_SUB" -h "$MQTT_HOST" -t "$ACK_TOPIC" -R 2>/dev/null | while IFS= read -r line; do
      # Segnala che siamo pronti al primo messaggio ricevuto (o subito)
      [ ! -f "$SUB_READY_FLAG" ] && touch "$SUB_READY_FLAG"
      echo "$line"
    done > "$SUB_FIFO"
  ) &
  SUB_PID=$!

  dbg "Subscriber avviato pid=$SUB_PID fifo=$SUB_FIFO"

  # Aspetta che il subscriber sia pronto (max 2 secondi)
  local wait_count=0
  while [ ! -f "$SUB_READY_FLAG" ] && [ $wait_count -lt 20 ]; do
    sleep 0.1
    wait_count=$((wait_count + 1))
    # Dopo 1 secondo, assumiamo sia pronto anche senza conferma
    if [ $wait_count -ge 10 ]; then
      dbg "Subscriber probabilmente pronto (timeout attesa flag)"
      break
    fi
  done

  # Ulteriore piccolo buffer di sicurezza
  sleep 0.3
  dbg "Subscriber pronto"
}

# Legge dalla FIFO con timeout
wait_ack_from_fifo() {
  local typ="$1"; local since="$2"; local mid="$3"
  local deadline=$(( $(date +%s) + ACK_TIMEOUT ))
  local line

  dbg "Attendo ACK per cmdType='$typ' msgId='$mid' since=$since window=${ACK_WINDOW_MS}ms"

  while [ "$(date +%s)" -lt "$deadline" ]; do
    if read -r -t 1 line < "$SUB_FIFO" 2>/dev/null; then
      dbg "RX RAW: $line"

      # Verifica JSON valido
      if ! echo "$line" | jq empty 2>/dev/null; then
        dbg "  Non è JSON valido, ignoro"
        continue
      fi

      # Estrai campi
      local msg_type=$(echo "$line" | jq -r '.cmdType // ""' 2>/dev/null)
      local msg_id=$(echo "$line" | jq -r '.msgId // ""' 2>/dev/null)
      local msg_ts=$(echo "$line" | jq -r '.ts // 0' 2>/dev/null)

      dbg "  cmdType=$msg_type msgId=$msg_id ts=$msg_ts"

      # Match cmdType
      if [ "$msg_type" != "$typ" ]; then
        dbg "  cmdType non matcha (atteso '$typ'), ignoro"
        continue
      fi

      # Match msgId O timestamp window
      if [ -n "$msg_id" ] && [ "$msg_id" = "$mid" ]; then
        dbg "  ✓ MATCH per msgId!"
        echo "$line"
        return 0
      fi

      # Fallback su timestamp window se msgId è null
      if [[ "$msg_ts" =~ ^[0-9]+$ ]] && [ "$msg_ts" -gt 0 ]; then
        local window_end=$((since + ACK_WINDOW_MS))
        dbg "  Check timestamp: $msg_ts in [$since, $window_end]?"
        if [ "$msg_ts" -ge "$since" ] && [ "$msg_ts" -le "$window_end" ]; then
          dbg "  ✓ MATCH per timestamp window!"
          echo "$line"
          return 0
        else
          dbg "  Timestamp fuori window"
        fi
      fi
    fi
  done

  dbg "Timeout in attesa di ACK"
  echo ""
  return 0
}

send_cmd() {
  local typ="$1"
  local ts msgid payload ack ats

  # 1) Avvia subscriber PRIMA di generare timestamp e msgid
  start_ack_sub

  # 2) Genera timestamp e payload DOPO che il sub è pronto
  ts=$(date +%s%3N)
  msgid=$(gen_msgid)
  payload=$(printf '{"type":"%s","ts":%s,"msgId":"%s"}' "$typ" "$ts" "$msgid")

  echo "• POST ${CMD_PATH} payload=${payload}"

  # 3) POST CoAP
  if ! "$COAP" -m post -t application/json -e "$payload" "${BASE}${CMD_PATH}" >/dev/null 2>&1; then
    dbg "coap-client ha restituito errore (potrebbe essere solo EMPTY ACK CoAP)."
  fi

  echo "• ACK:"

  # 4) Attendi ACK
  ack=$(wait_ack_from_fifo "$typ" "$ts" "$msgid")

  if [ -n "$ack" ]; then
    printf '%s\n' "$ack" | jq -r '"cmdType=\(.cmdType) status=\(.status) message=\(.message) ts=\(.ts) msgId=\(.msgId // "-")"'
    ats=$(printf '%s' "$ack" | jq -r 'try .ts catch 0' 2>/dev/null || echo 0)
    if [[ "$ats" =~ ^[0-9]+$ ]] && [[ "$ats" -gt 0 ]] && [[ "$ts" =~ ^[0-9]+$ ]]; then
      echo "• Latenza ≈ $((ats - ts)) ms"
    fi
  else
    echo "⚠ Nessun ACK per cmdType='${typ}' entro ${ACK_TIMEOUT}s (window ${ACK_WINDOW_MS}ms)"
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