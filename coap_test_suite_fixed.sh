#!/bin/bash
# Script di test automatico per gli endpoint CoAP della microfactory.
# Prerequisiti: coap-client installato (es. `sudo apt-get install libcoap2-bin` su Debian/Ubuntu).
# Lo script rileva automaticamente se eseguito in WSL per usare l'IP corretto.

set -euo pipefail

echo "=== Test Suite CoAP Microfactory ==="
echo

# --- Configurazione ---
COAP_CLIENT=${COAP_CLIENT:-coap-client} # Default: coap-client. Può essere cambiato se il binario ha un altro nome.
TIMEOUT=${TIMEOUT:-10}

# URI di Base: se non impostato, in WSL2 viene ricavato l'IP di Windows per raggiungere il server.
if [[ -z "${BASE_URI:-}" ]]; then
  if grep -qi microsoft /proc/version 2>/dev/null; then
    # Siamo in WSL, ricaviamo l'IP dell'host Windows
    HOST_IP=$(powershell.exe -NoProfile -Command \
      "(Get-NetIPAddress -AddressFamily IPv4 -InterfaceAlias 'vEthernet (WSL*)' | Select -First 1 -Expand IPAddress)" \
      | tr -d '\r')
    BASE_URI="coap://${HOST_IP}:5683"
  else
    # Ambiente non-WSL (Linux nativo, Mac, etc.)
    BASE_URI="coap://127.0.0.1:5683"
  fi
fi

# --- Funzioni di utilità per l'output ---
green(){ echo -e "\e[32m$*\e[0m"; }
red()  { echo -e "\e[31m$*\e[0m"; }
pass(){ green "  ✓ Test superato: $*"; }
fail(){ red   "  ✗ Test fallito: $*"; }

# Funzione per verificare il codice di risposta CoAP dall'output verboso.
# $1: codice atteso (es. "2.04")
# $2...: comando coap-client da eseguire
check_code() {
  local expected_code="$1"; shift
  local output
  # Esegue il comando e cattura l'output verboso, sopprimendo errori di connessione
  output=$("$@" -v 8 -s "$TIMEOUT" 2>&1) || true

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
echo "Attendo 3 secondi per l'avvio del server..."
sleep 3
echo

# --- Inizio dei Test ---

echo "--- Test 1: Verifica Server Attivo e Discovery (.well-known/core) ---"
LINKS=$($COAP_CLIENT -m get "$BASE_URI/.well-known/core" -s "$TIMEOUT" 2>/dev/null || true)
if [[ -z "$LINKS" ]]; then
    fail "Nessuna risposta da /.well-known/core. Il server è in esecuzione?"
    exit 1
fi
pass "Il server ha risposto a /.well-known/core."
echo "Risorse pubblicate:"
echo "$LINKS" | sed -n 's/.*<\([^>]*\)>.*/ - \1/p'
echo "$LINKS" | grep -q '<\/factory>'      || red  "  ! ATTENZIONE: Manca /factory nella discovery!"
echo "$LINKS" | grep -q '<\/factory\/cmd>' || red  "  ! ATTENZIONE: Manca /factory/cmd nella discovery!"
echo

echo "--- Test 2: Recupero Stato Fabbrica (GET /factory) ---"
# Questo test è best-effort: alcune implementazioni potrebbero rispondere 4.05 (Method Not Allowed)
# se /factory è solo un punto di routing. Entrambi sono accettabili.
RESPONSE=$($COAP_CLIENT -m get "$BASE_URI/factory" -s "$TIMEOUT" 2>&1 || true)
if echo "$RESPONSE" | grep -q "2.05"; then
    pass "GET /factory ha risposto con 2.05 Content."
    echo "Corpo della risposta:"
    echo "$RESPONSE" | tail -n 1
elif echo "$RESPONSE" | grep -q "4.05"; then
    pass "GET /factory ha risposto con 4.05 Method Not Allowed (comportamento accettabile)."
else
    fail "GET /factory non ha restituito il codice atteso (2.05 o 4.05)."
fi
echo

# --- Test 3: Interazione con Dispositivi ---
CELL_ID="cell-01"
echo "--- Test 3a: Elenco dispositivi in '$CELL_ID' (GET /factory/$CELL_ID/devices) ---"
DEVICE_LIST_JSON=$($COAP_CLIENT -m get "$BASE_URI/factory/$CELL_ID/devices" -s "$TIMEOUT" 2>/dev/null || true)
if [[ -z "$DEVICE_LIST_JSON" ]]; then
    fail "Nessuna risposta da /factory/$CELL_ID/devices."
else
    pass "Ottenuto elenco dispositivi."
    echo "Risposta: ${DEVICE_LIST_JSON}"
fi

# Estrae l'ID del primo robot trovato per i test successivi
ROBOT_ID=$(echo "$DEVICE_LIST_JSON" | grep -oE '\{[^}]*"type":"robot"[^}]*\}' | grep -oE '"id":"[^"]+"' | head -n1 | sed 's/"id":"\([^"]*\)"/\1/')

if [[ -z "${ROBOT_ID}" ]]; then
  echo "Nessun robot trovato nella cella '$CELL_ID'. Salto i test specifici per il robot."
else
  echo "Trovato robot con ID: $ROBOT_ID. Eseguo test su di esso."
  STATE_URI="$BASE_URI/factory/$CELL_ID/robot/$ROBOT_ID/state"
  CMD_URI="$BASE_URI/factory/$CELL_ID/robot/$ROBOT_ID/cmd"

  echo "--- Test 3b: GET stato robot (JSON, text, SenML) ---"
  $COAP_CLIENT -m get "$STATE_URI" -s "$TIMEOUT" 2>/dev/null | head -n1 && pass "GET stato (JSON) OK" || fail "GET stato (JSON) fallito"
  $COAP_CLIENT -m get -A 0 "$STATE_URI" -s "$TIMEOUT" 2>/dev/null | head -n1 && pass "GET stato (text/plain) OK" || fail "GET stato (text/plain) fallito"
  $COAP_CLIENT -m get -A 110 "$STATE_URI" -s "$TIMEOUT" 2>/dev/null | head -n1 && pass "GET stato (SenML+JSON) OK" || fail "GET stato (SenML+JSON) fallito"

  echo "--- Test 3c: POST comandi al robot ---"
  check_code "2.04" $COAP_CLIENT -m post -e "RESET" -t 0 "$CMD_URI"
  check_code "2.04" $COAP_CLIENT -m post -t 50 -e '{"cmd":"START"}' "$CMD_URI"
fi
echo

echo "--- Test 4: Comandi Globali (/factory/cmd) ---"
check_code "2.04" $COAP_CLIENT -m post -e "RESET" -t 0 "$BASE_URI/factory/cmd"
check_code "2.04" $COAP_CLIENT -m post -t 50 -e '{"cmd":"START"}' "$BASE_URI/factory/cmd"
echo

echo "--- Test 5: Test Negativi (gestione errori) ---"
echo "Test 5a: Path inesistente (atteso 4.04 Not Found)"
check_code "4.04" $COAP_CLIENT -m get "$BASE_URI/path/inesistente"

echo "Test 5b: Dispositivo inesistente (atteso 4.04 Not Found)"
check_code "4.04" $COAP_CLIENT -m get "$BASE_URI/factory/cell-99/robot/fake-robot-001/state"

echo "Test 5c: Content-Format non supportato (atteso 4.06 Not Acceptable)"
check_code "4.06" $COAP_CLIENT -m post -t 41 -e "<test/>" "$BASE_URI/factory/cmd"

echo "Test 5d: Payload vuoto per un comando (atteso 4.00 Bad Request)"
check_code "4.00" $COAP_CLIENT -m post -e "" "$BASE_URI/factory/cmd"
echo

green "=== Test Suite CoAP completata con successo ==="