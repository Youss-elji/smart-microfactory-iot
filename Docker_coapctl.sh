#!/usr/bin/env bash
# Docker_coapctl.sh — Utility per testare gli endpoint CoAP della Smart Microfactory
# Uso:
#   ./Docker_coapctl.sh ping
#   ./Docker_coapctl.sh devices
#   ./Docker_coapctl.sh status
#   ./Docker_coapctl.sh start|stop|reset
#   (opz.) variabili: CONTAINER=microfactory-app CELL=cell-01 ROBOT=robot-001

set -euo pipefail

# ---- Config di default (sovrascrivibili da env) ------------------------------
CONTAINER="${CONTAINER:-microfactory-app}"
CELL="${CELL:-cell-01}"
ROBOT="${ROBOT:-robot-001}"
PORT="${PORT:-5683}"
BASE="${BASE:-factory}"

# ---- Scoperta IP del container (rete docker) --------------------------------
container_ip() {
  docker inspect -f '{{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$CONTAINER"
}

# ---- Disponibilità coap-client (host vs container) ---------------------------
find_client() {
  if command -v coap-client-openssl >/dev/null 2>&1; then
    echo "coap-client-openssl"
  elif command -v coap-client >/dev/null 2>&1; then
    echo "coap-client"
  else
    echo ""  # non trovato
  fi
}

# ---- Wrapper per chiamare CoAP ----------------------------------------------
# Se c'è un client sull'host, usa IP del container; altrimenti esegue dentro al container.
coap_get() {
  local path="$1"
  local CLIENT
  CLIENT="$(find_client || true)"
  if [[ -n "$CLIENT" ]]; then
    local IP; IP="$(container_ip)"
    "$CLIENT" -m get "coap://${IP}:${PORT}${path}"
  else
    docker exec -it "$CONTAINER" sh -lc \
      "coap-client -m get coap://localhost:${PORT}${path}"
  fi
}

coap_post_json() {
  local path="$1"
  local json="$2"
  local CLIENT
  CLIENT="$(find_client || true)"
  if [[ -n "$CLIENT" ]]; then
    local IP; IP="$(container_ip)"
    "$CLIENT" -m post -T application/json -e "$json" "coap://${IP}:${PORT}${path}"
  else
    docker exec -it "$CONTAINER" sh -lc \
      "coap-client -m post -T application/json -e '$json' coap://localhost:${PORT}${path}"
  fi
}

# ---- Helpers per endpoint ----------------------------------------------------
PATH_BASE="/${BASE}"
PATH_DEVICES="${PATH_BASE}/${CELL}/devices"
PATH_ROBOT_STATE="${PATH_BASE}/${CELL}/robot/${ROBOT}/"
PATH_ROBOT_CMD="${PATH_BASE}/${CELL}/robot/${ROBOT}/cmd"

# ---- Attesa server pronto (retry su .well-known/core) ------------------------
wait_ready() {
  local retries="${1:-20}"
  local delay="${2:-0.5}"
  for ((i=1; i<=retries; i++)); do
    if coap_get "/.well-known/core" >/dev/null 2>&1; then
      return 0
    fi
    sleep "$delay"
  done
  echo "Timeout: server CoAP non raggiungibile" >&2
  return 1
}

# ---- Comandi -----------------------------------------------------------------
cmd_ping() {
  echo "• /.well-known/core:"
  $COAP -m get "$BASE_URI/.well-known/core"
}

cmd_devices() {
  echo "• GET $ROBOT_PATH/../devices"
  $COAP -m get "$BASE_URI/factory/cell-01/devices"
}

cmd_status() {
  echo "• GET stato robot (JSON): $ROBOT_PATH/"
  $COAP -m get "$BASE_URI${ROBOT_PATH}/"
  echo
  echo "• GET stato robot (text/plain con SenML plain): $ROBOT_PATH/"
  $COAP -m get -t text/plain "$BASE_URI${ROBOT_PATH}/"
}

post_cmd() {
  local what="$1"               # START | STOP | RESET
  local payload="{\"cmd\":\"$what\"}"
  echo "• POST $ROBOT_PATH/cmd  payload=$payload"
  printf '%s' "$payload" \
    | $COAP -m post -t application/json -f - "$BASE_URI${ROBOT_PATH}/cmd"
}


# ---- Usage -------------------------------------------------------------------
usage() {
  cat <<EOF
Uso: $(basename "$0") <ping|devices|status|start|stop|reset>

Env opzionali:
  CONTAINER=$CONTAINER   (nome del container app)
  CELL=$CELL             (cell-id)
  ROBOT=$ROBOT           (robot-id)
  PORT=$PORT             (porta CoAP; default 5683)
  BASE=$BASE             (radice API; default /factory)

Esempi:
  ./coapctl.sh ping
  ./coapctl.sh devices
  ./coapctl.sh status
  ./coapctl.sh start
  CELL=cell-02 ROBOT=robot-002 ./coapctl.sh reset
EOF
}

# ---- Main --------------------------------------------------------------------
case "${1:-}" in
  ping)    cmd_ping ;;
  devices) cmd_devices ;;
  status)  cmd_status ;;
  start)   post_cmd START ;;
  stop)    post_cmd STOP ;;
  reset)   post_cmd RESET ;;
  *) echo "Uso: $0 {ping|devices|status|start|stop|reset}" ; exit 1 ;;
esac