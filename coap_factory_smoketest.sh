#!/usr/bin/env bash
# Smoke test CoAP per smart-microfactory (GET/POST dinamici)
# Funziona in WSL2 (Ubuntu). Richiede 'coap-client-openssl' (libcoap3-bin).

set -u

# --- Parametri ---------------------------------------------------------------
PORT="${PORT:-5683}"
TIMEOUT="${TIMEOUT:-10}"
COAP_BIN="${COAP_BIN:-/usr/bin/coap-client-openssl}"   # fallback più giù se non c'è
ACCEPT_JSON=50            # MediaTypeRegistry.APPLICATION_JSON
ACCEPT_SENML_JSON=112     # SenML+JSON
ACCEPT_TEXT=0

# --- Trova coap-client -------------------------------------------------------
if ! command -v "$COAP_BIN" >/dev/null 2>&1; then
  for alt in /usr/bin/coap-client-openssl /usr/bin/coap-client-gnutls /usr/bin/coap-client-notls; do
    if command -v "$alt" >/dev/null 2>&1; then COAP_BIN="$alt"; break; fi
  done
fi
if ! command -v "$COAP_BIN" >/dev/null 2>&1; then
  echo "ERRORE: coap-client non trovato. Installa: sudo apt-get install -y libcoap3-bin"
  exit 1
fi

# --- IP Windows (interfaccia WSL) -------------------------------------------
WIN_IP="${WIN_IP:-$(
  powershell.exe -NoProfile -Command \
    "(Get-NetIPAddress -AddressFamily IPv4 -InterfaceAlias 'vEthernet (WSL*)' | Select -First 1 -Expand IPAddress)" \
  | tr -d '\r'
)}"

if [[ -z "$WIN_IP" ]]; then
  echo "ERRORE: impossibile ricavare l'IP Windows (WSL)."
  exit 1
fi

BASE="coap://$WIN_IP:$PORT"
echo "===> COAP client: $COAP_BIN"
echo "===> Base URI   : $BASE"
echo

coap_get() {
  local path="$1" accept="$2"
  if [[ -n "$accept" ]]; then
    "$COAP_BIN" -m get -A "$accept" "$BASE$path" -s "$TIMEOUT"
  else
    "$COAP_BIN" -m get "$BASE$path" -s "$TIMEOUT"
  fi
}

resp_code_from_verbose() {
  # Estrae il "Code: X.XX" dall'output verboso di libcoap
  sed -n 's/^<-- .*Code: \([0-9.]*\).*/\1/p' | tail -n 1
}

coap_post_text() {
  local path="$1" payload="$2"
  echo "[POST text/plain] $path  payload=\"$payload\""
  local code
  code=$("$COAP_BIN" -m post -t 0 -e "$payload" "$BASE$path" -s "$TIMEOUT" -v 4 2>&1 | resp_code_from_verbose)
  echo "   => Code: ${code:-N/A}"
  echo
}

coap_post_json() {
  local path="$1" json="$2"
  echo "[POST application/json] $path  payload=$json"
  local code
  code=$("$COAP_BIN" -m post -t 50 -e "$json" "$BASE$path" -s "$TIMEOUT" -v 4 2>&1 | resp_code_from_verbose)
  echo "   => Code: ${code:-N/A}"
  echo
}

# --- 1) scopri le risorse ----------------------------------------------------
echo "==> Lettura .well-known/core ..."
links="$("$COAP_BIN" -m get "$BASE/.well-known/core" -s "$TIMEOUT" 2>/dev/null || true)"
if [[ -z "$links" ]]; then
  echo "ERRORE: nessuna risposta da $BASE/.well-known/core"
  exit 1
fi

# Estrai i path tra <...> ed elimina eventuale schema/host iniziale
paths="$(echo "$links" | sed -n 's/.*<\([^>]*\)>.*/\1/p' | sed 's#^coap://[^/]*/#/#')"

echo "==> Risorse pubblicate:"
echo "$paths" | sed 's/^/  - /'
echo

# --- 2) pick automatico di path utili ---------------------------------------
factory_path="$(echo "$paths" | grep -E '^/factory($|[^a-zA-Z0-9_/-])' | head -n 1)"
devices_path="$(echo "$paths" | grep -E '^/factory/.*/devices$' | head -n 1)"
robot_state_path="$(echo "$paths" | grep -E '^/factory/.*/robot/.*/state$' | head -n 1)"
robot_cmd_path="$(echo "$paths" | grep -E '^/factory/.*/robot/.*/cmd$' | head -n 1)"

# --- 3) GET di base ----------------------------------------------------------
if [[ -n "$factory_path" ]]; then
  echo "=== GET $factory_path (text/plain atteso 4.05 se non previsto) ==="
  coap_get "$factory_path" "$ACCEPT_TEXT" || true
  echo
fi

if [[ -n "$devices_path" ]]; then
  echo "=== GET $devices_path (JSON) ==="
  coap_get "$devices_path" "$ACCEPT_JSON" || true
  echo
fi

if [[ -n "$robot_state_path" ]]; then
  echo "=== GET $robot_state_path (JSON) ==="
  coap_get "$robot_state_path" "$ACCEPT_JSON" || true
  echo "=== GET $robot_state_path (SenML JSON) ==="
  coap_get "$robot_state_path" "$ACCEPT_SENML_JSON" || true
  echo "=== GET $robot_state_path (text/plain) ==="
  coap_get "$robot_state_path" "$ACCEPT_TEXT" || true
  echo
else
  echo "WARN: Nessun path robot/*/state trovato."
fi

# --- 4) POST ai comandi ------------------------------------------------------
if [[ -n "$robot_cmd_path" ]]; then
  coap_post_text "$robot_cmd_path" "RESET"
  coap_post_json "$robot_cmd_path" '{"cmd":"RESET"}'
else
  echo "ATTENZIONE: nessun path '/cmd' trovato tra i link!"
  echo "Possibili cause:"
  echo "  - la risorsa DeviceCommandResource non è registrata (add(new DeviceCommandResource(...)))"
  echo "  - stai eseguendo un JAR non aggiornato (ricostruisci lo shaded: mvn -DskipTests=true clean package)"
  echo "  - il server è partito con una configurazione/branch diversa"
  echo
  echo "Suggerimento: verifica manualmente i link con:"
  echo "  $COAP_BIN -m get \"$BASE/.well-known/core\" -s $TIMEOUT"
fi

echo "==> Smoke test completato."
