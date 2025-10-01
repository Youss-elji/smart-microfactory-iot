#!/usr/bin/env bash
set -euo pipefail

# -------------------------------------------------------------------
# Smart Microfactory – CoAP smoke test (WSL2 friendly) - FIXED
# -------------------------------------------------------------------

COAP=${COAP:-/usr/bin/coap-client-openssl}
TIMEOUT=${TIMEOUT:-10}

# Base URI: se non impostato, in WSL2 ricavo l'IP di Windows
if [[ -z "${BASE:-}" ]]; then
  if grep -qi microsoft /proc/version 2>/dev/null; then
    WIN_IP=$(powershell.exe -NoProfile -Command \
      "(Get-NetIPAddress -AddressFamily IPv4 -InterfaceAlias 'vEthernet (WSL*)' | Select -First 1 -Expand IPAddress)" \
      | tr -d '\r')
    BASE="coap://${WIN_IP}:5683"
  else
    BASE="coap://127.0.0.1:5683"
  fi
fi

green(){ echo -e "\e[32m$*\e[0m"; }
red()  { echo -e "\e[31m$*\e[0m"; }
pass(){ green "  ✓ $*"; }
fail(){ red   "  ✗ $*"; }

# Greppa il code (4.xx/2.xx) dalla verbose output (-v 8)
check_code() {
  local want="$1"; shift
  if "$@" -v 8 -s "$TIMEOUT" 2>&1 | grep -q "$want"; then
    pass "$want OK"
    return 0
  else
    fail "atteso $want"; return 1
  fi
}

echo "==> COAP client : $COAP"
echo "==> Base URI    : $BASE"
echo

# -------------------------------------------------------------------
# /.well-known/core
# -------------------------------------------------------------------
echo "==> Lettura /.well-known/core ..."
LINKS=$($COAP -m get "$BASE/.well-known/core" -s "$TIMEOUT" 2>/dev/null || true)
echo "${LINKS:-<nessuna risposta>}"
echo

echo "==> Risorse pubblicate:"
COUNT=$(echo "$LINKS" | sed -n 's/.*<\([^>]*\)>.*/\1/p' | tee /dev/stderr | wc -l)
echo "$LINKS" | sed -n 's/.*<\([^>]*\)>.*/ - \1/p'
[[ $COUNT -eq 0 ]] && fail "nessun link esposto (server non partito o porta errata?)"
echo

# Avvisi rapidi su risorse chiave
echo "$LINKS" | grep -q '<\/factory>'      || red  "  ! manca /factory nella discovery"
echo "$LINKS" | grep -q '<\/factory\/cmd>' || red  "  ! manca /factory/cmd nella discovery"
echo

# -------------------------------------------------------------------
# /factory (best-effort: alcune impl rispondono 4.05)
# -------------------------------------------------------------------
echo "==> GET /factory (expect 2.05 o 4.05 a seconda della tua impl)"
FACTORY_RESP=$($COAP -m get "$BASE/factory" -s "$TIMEOUT" 2>&1 || true)
echo "$FACTORY_RESP" | sed -n 's/^v: //p' | sed -n '1,3p' || true
# Se ottieni 2.05, stampa il body
if echo "$FACTORY_RESP" | grep -q "2\.05"; then
  echo "$FACTORY_RESP" | tail -n 1
fi
echo

# -------------------------------------------------------------------
# /factory/<cell>/devices e state/cmd del primo robot
# -------------------------------------------------------------------
CELL="cell-01"
DEV_JSON=$($COAP -m get "$BASE/factory/$CELL/devices" -s "$TIMEOUT" 2>/dev/null || true)
echo "==> /factory/$CELL/devices => ${DEV_JSON:-<nessuna risposta>}"

# FIX: Parser robusto per estrarre robot ID dal JSON
# Cerca pattern: "type":"robot","id":"XXX" oppure "id":"XXX","type":"robot"
ROBOT_ID=$(
  echo "$DEV_JSON" \
  | grep -oE '\{[^}]*"type":"robot"[^}]*\}' \
  | grep -oE '"id":"[^"]+"' \
  | head -n1 \
  | sed 's/"id":"\([^"]*\)"/\1/'
)

if [[ -z "${ROBOT_ID:-}" ]]; then
  echo "Nessun robot nel JSON dispositivi, salto i test per-device."
else
  echo "==> Robot rilevato: $ROBOT_ID"
  STATE="$BASE/factory/$CELL/robot/$ROBOT_ID/state"
  CMD="$BASE/factory/$CELL/robot/$ROBOT_ID/cmd"

  echo "==> GET state (JSON default)"
  $COAP -m get "$STATE" -s "$TIMEOUT" 2>/dev/null | head -n1 || fail "GET state fallito"

  echo "==> GET state (text/plain -A 0)"
  $COAP -m get -A 0 "$STATE" -s "$TIMEOUT" 2>/dev/null | head -n1 || fail "GET text/plain fallito"

  echo "==> GET state (SenML JSON -A 110)"
  $COAP -m get -A 110 "$STATE" -s "$TIMEOUT" 2>/dev/null | head -n1 || fail "GET SenML fallito"

  echo "==> POST cmd text/plain RESET con -t 0 (expect 2.04)"
  check_code "2\.04" $COAP -m post -e "RESET" -t 0 "$CMD"

  echo "==> POST cmd application/json START con -t 50 (expect 2.04)"
  check_code "2\.04" $COAP -m post -t 50 -e '{"cmd":"START"}' "$CMD"
fi
echo

# -------------------------------------------------------------------
# Global command tests
# -------------------------------------------------------------------
echo "==> POST /factory/cmd RESET con -t 0 (expect 2.04)"
check_code "2\.04" $COAP -m post -e "RESET" -t 0 "$BASE/factory/cmd"

echo "==> POST /factory/cmd START JSON con -t 50 (expect 2.04)"
check_code "2\.04" $COAP -m post -t 50 -e '{"cmd":"START"}' "$BASE/factory/cmd"

echo

# -------------------------------------------------------------------
# Negative checks
# -------------------------------------------------------------------
echo "==> NEGATIVE: path inesistente (expect 4.04)"
check_code "4\.04" $COAP -m get "$BASE/nope"

echo "==> NEGATIVE: device inesistente (expect 4.04)"
check_code "4\.04" $COAP -m get "$BASE/factory/cell-99/robot/fake-001/state"

echo "==> NEGATIVE: Content-Format non supportato (expect 4.06)"
# Prova con content-type XML (41) che non è supportato
OUT=$($COAP -m post -t 41 -e "TEST" "$BASE/factory/cmd" -v 8 -s "$TIMEOUT" 2>&1 || true)
if echo "$OUT" | grep -q "4\.06"; then
  pass "4.06 OK (Content-Format rifiutato)"
else
  fail "atteso 4.06 per Content-Format non supportato; output:"
  echo "$OUT" | grep -E "^(v:|4\.|2\.)" || true
fi

echo "==> NEGATIVE: payload vuoto (expect 4.00)"
OUT=$($COAP -m post -t 0 -e "" "$BASE/factory/cmd" -v 8 -s "$TIMEOUT" 2>&1 || true)
if echo "$OUT" | grep -q "4\.00"; then
  pass "4.00 OK (payload vuoto rifiutato)"
else
  fail "atteso 4.00 per payload vuoto"
fi

echo
echo "==> Smoke test completato."