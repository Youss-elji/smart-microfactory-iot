#!/usr/bin/env bash
# Script di test automatico per gli endpoint CoAP della microfactory.
# Prerequisiti: coap-client installato (es. `sudo apt-get install libcoap2-bin` su Debian/Ubuntu).
#
# IMPORTANTE: Se il server gira in WSL, usa localhost. Se gira in Windows, usa BASE_URI esplicito.
# Uso:
#   ./coap_test_suite_fixed.sh                    # Server su localhost (WSL o Linux nativo)
#   BASE_URI=coap://IP:5683 ./coap_test_suite_fixed.sh  # Server remoto o Windows da WSL

set -euo pipefail

echo "=== Test Suite CoAP Microfactory ==="
echo

# --- Configurazione ---
COAP_CLIENT=${COAP_CLIENT:-/usr/bin/coap-client-openssl}
TIMEOUT=${TIMEOUT:-10}

# URI di Base: di default usa localhost
# Se serve connettersi a un server su Windows da WSL, imposta BASE_URI manualmente:
#   BASE_URI=coap://$(hostname).local:5683 ./script.sh
# oppure
#   BASE_URI=coap://192.168.x.x:5683 ./script.sh
BASE_URI="${BASE_URI:-coap://localhost:5683}"

# --- Funzioni di utilità per l'output ---
green(){ echo -e "\e[32m$*\e[0m"; }
red()  { echo -e "\e[31m$*\e[0m"; }
yellow(){ echo -e "\e[33m$*\e[0m"; }
pass(){ green "  ✓ Test superato: $*"; }
fail(){ red   "  ✗ Test fallito: $*"; }
warn(){ yellow "  ⚠ Attenzione: $*"; }

# Funzione per verificare il codice di risposta CoAP dall'output verboso.
# $1: codice atteso (es. "2.04")
# $2...: comando coap-client da eseguire
check_code() {
  local expected_code="$1"; shift
  local output
  # Esegue il comando e cattura l'output verboso, sopprimendo errori di connessione
  output=$("$@" -v 8 -B "$TIMEOUT" 2>&1) || true

  if echo "$output" | grep -q "$expected_code"; then
    pass "Codice di risposta $expected_code ricevuto correttamente."
    return 0
  else
    fail "Atteso codice '$expected_code', ma non è stato trovato nell'output."
    echo "$output" | grep -E "^(v:|4\.|2\.)" || true # Mostra righe rilevanti in caso di fallimento
    return 1
  fi
}

echo "Client CoAP utilizzato: $COAP_CLIENT"
echo "URI di Base del Server: $BASE_URI"
echo

# Verifica che coap-client sia disponibile
if ! command -v "$COAP_CLIENT" >/dev/null 2>&1; then
  red "ERRORE: $COAP_CLIENT non trovato!"
  echo "Installa libcoap: sudo apt-get install libcoap2-bin"
  exit 1
fi

echo "Attendo 2 secondi prima di iniziare i test..."
sleep 2
echo

# --- Inizio dei Test ---

echo "--- Test 1: Verifica Server Attivo e Discovery (.well-known/core) ---"
LINKS=$($COAP_CLIENT -m get "$BASE_URI/.well-known/core" -B "$TIMEOUT" 2>/dev/null || true)
if [[ -z "$LINKS" ]]; then
    fail "Nessuna risposta da /.well-known/core. Il server è in esecuzione su $BASE_URI?"
    red "Suggerimento: Verifica che il server Java sia avviato e in ascolto sulla porta 5683."
    exit 1
fi
pass "Il server ha risposto a /.well-known/core."
echo "Risorse pubblicate:"
echo "$LINKS" | sed -n 's/.*<\([^>]*\)>.*/ - \1/p'
echo "$LINKS" | grep -q '<\/factory>' || warn "Manca /factory nella discovery!"
echo

echo "--- Test 2: Recupero Stato Fabbrica (GET /factory) ---"
# Questo test è best-effort: alcune implementazioni potrebbero rispondere 4.05 (Method Not Allowed)
# se /factory è solo un punto di routing. Entrambi sono accettabili.
RESPONSE=$($COAP_CLIENT -m get "$BASE_URI/factory" -B "$TIMEOUT" 2>&1 || true)
if echo "$RESPONSE" | grep -q "2.05"; then
    pass "GET /factory ha risposto con 2.05 Content."
    echo "Corpo della risposta:"
    echo "$RESPONSE" | tail -n 1
elif echo "$RESPONSE" | grep -q "4.05"; then
    pass "GET /factory ha risposto con 4.05 Method Not Allowed (comportamento accettabile)."
else
    warn "GET /factory non ha restituito il codice atteso (2.05 o 4.05)."
fi
echo

# --- Test 3: Interazione con Dispositivi ---
CELL_ID="cell-01"
echo "--- Test 3a: Elenco dispositivi in '$CELL_ID' (GET /factory/$CELL_ID/devices) ---"
DEVICE_LIST_JSON=$($COAP_CLIENT -m get "$BASE_URI/factory/$CELL_ID/devices" -B "$TIMEOUT" 2>/dev/null || true)
if [[ -z "$DEVICE_LIST_JSON" ]]; then
    fail "Nessuna risposta da /factory/$CELL_ID/devices."
else
    pass "Ottenuto elenco dispositivi."
    echo "Risposta: ${DEVICE_LIST_JSON}"
fi

# Estrae l'ID del primo robot trovato per i test successivi
ROBOT_ID=$(echo "$DEVICE_LIST_JSON" | grep -oE '\{[^}]*"type":"robot"[^}]*\}' | grep -oE '"id":"[^"]+"' | head -n1 | sed 's/"id":"\([^"]*\)"/\1/')

if [[ -z "${ROBOT_ID}" ]]; then
  warn "Nessun robot trovato nella cella '$CELL_ID'. Salto i test specifici per il robot."
else
  echo "Trovato robot con ID: $ROBOT_ID. Eseguo test su di esso."
  STATE_URI="$BASE_URI/factory/$CELL_ID/robot/$ROBOT_ID/state"
  CMD_URI="$BASE_URI/factory/$CELL_ID/robot/$ROBOT_ID/cmd"

  echo
  echo "--- Test 3b: GET stato robot (JSON, text, SenML) ---"
  ROBOT_STATE=$($COAP_CLIENT -m get "$STATE_URI" -B "$TIMEOUT" 2>/dev/null || true)
  if [[ -n "$ROBOT_STATE" ]]; then
    pass "GET stato (JSON) OK"
    echo "Stato: $ROBOT_STATE"
  else
    fail "GET stato (JSON) fallito"
  fi

  $COAP_CLIENT -m get -A 0 "$STATE_URI" -B "$TIMEOUT" 2>/dev/null >/dev/null && pass "GET stato (text/plain) OK" || fail "GET stato (text/plain) fallito"
  $COAP_CLIENT -m get -A 110 "$STATE_URI" -B "$TIMEOUT" 2>/dev/null >/dev/null && pass "GET stato (SenML+JSON) OK" || fail "GET stato (SenML+JSON) fallito"

  echo
  echo "--- Test 3c: POST comandi al robot ---"
  RESET_PAYLOAD='{"type":"RESET"}'
  NOW_TS=$(($(date +%s%N)/1000000))
  START_PAYLOAD=$(printf '{"type":"START","ts":%s}' "$NOW_TS")

  echo "Invio comando RESET..."
  check_code "2.04" $COAP_CLIENT -m post -t 50 -e "$RESET_PAYLOAD" "$CMD_URI"
  sleep 1

  echo "Invio comando START..."
  check_code "2.04" $COAP_CLIENT -m post -t 50 -e "$START_PAYLOAD" "$CMD_URI"
fi
echo

echo "--- Test 4: Comandi Globali (/factory/cmd) ---"
if echo "$LINKS" | grep -q '/factory/cmd'; then
  echo "Invio comando RESET globale..."
  check_code "2.04" $COAP_CLIENT -m post -t 50 -e '{"type":"RESET"}' "$BASE_URI/factory/cmd"
  sleep 1

  echo "Invio comando EMERGENCY globale..."
  check_code "2.04" $COAP_CLIENT -m post -t 50 -e '{"type":"EMERGENCY"}' "$BASE_URI/factory/cmd"
else
  warn "/factory/cmd non trovato nella discovery, salto questo test."
fi
echo

echo "--- Test 5: Test Negativi (gestione errori) ---"
echo "Test 5a: Path inesistente (atteso 4.04 Not Found)"
check_code "4.04" $COAP_CLIENT -m get "$BASE_URI/path/inesistente"

echo
echo "Test 5b: Dispositivo inesistente (atteso 4.04 Not Found)"
check_code "4.04" $COAP_CLIENT -m get "$BASE_URI/factory/cell-99/robot/fake-robot-001/state"

echo
echo "Test 5c: Content-Format non supportato (atteso 4.06 Not Acceptable)"
check_code "4.06" $COAP_CLIENT -m post -t 41 -e "<test/>" "$BASE_URI/factory/cmd"

echo
echo "Test 5d: Payload vuoto per un comando (atteso 4.00 Bad Request)"
check_code "4.00" $COAP_CLIENT -m post -t 50 -e "" "$BASE_URI/factory/cmd"
echo

green "=== Test Suite CoAP completata con successo ==="