#!/usr/bin/env bash
set -euo pipefail

# -------------------------------------------------------------------
# Smart Microfactory — CoAP smoke test (WSL2 friendly)
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
$COAP -m get "$BASE/factory" -s "$TIMEOUT" -v 8 2>&1 | sed -n 's/^v: //p' | sed -n '1,3p' || true
echo

# -------------------------------------------------------------------
# /factory/<cell>/devices e state/cmd del primo robot
# -------------------------------------------------------------------
CELL="cell-01"
DEV_JSON=$($COAP -m get "$BASE/factory/$CELL/devices" -s "$TIMEOUT" 2>/dev/null || true)
echo "==> /factory/$CELL/devices => ${DEV_JSON:-<nessuna risposta>}"

# pesca il primo robot indipendentemente dall’ordine
ROBOT_ID=$(
  echo "$DEV_JSON" \
  | grep -Eo '"id":"[^"]+","type":"robot"|"type":"robot","id":"[^"]+"' || true \
  | sed -n 's/.*"id":"\([^"]*\)".*/\1/p' \
  | head -n1
)
if [[ -z "${ROBOT_ID:-}" ]]; then
  echo "Nessun robot nel JSON dispositivi, salto i test per-device."
else
  echo "==> Robot rilevato: $ROBOT_ID"
  STATE="$BASE/factory/$CELL/robot/$ROBOT_ID/state"
  CMD="$BASE/factory/$CELL/robot/$ROBOT_ID/cmd"

  echo "==> GET state (JSON default)"
  $COAP -m get "$STATE" -s "$TIMEOUT" | cat

  echo "==> GET state (text/plain -A 0)"
  $COAP -m get -A 0 "$STATE" -s "$TIMEOUT" | cat

  echo "==> GET state (SenML JSON -A 110)"
  $COAP -m get -A 110 "$STATE" -s "$TIMEOUT" | cat

  echo "==> POST cmd text/plain RESET (expect 2.04)"
  check_code "2\.04" $COAP -m post -e "RESET" "$CMD"

  echo "==> POST cmd application/json START (expect 2.04)"
  check_code "2\.04" $COAP -m post -t 50 -e '{"cmd":"START"}' "$CMD"
fi
echo

# -------------------------------------------------------------------
# Negative checks
# -------------------------------------------------------------------
echo "==> NEGATIVE: path inesistente (expect 4.04)"
check_code "4\.04" $COAP -m get "$BASE/nope"

echo "==> NEGATIVE: method not allowed su /factory (se non gestisce POST) (expect 4.05)"
check_code "4\.05" $COAP -m post -e "X" "$BASE/factory" || true

echo "==> NEGATIVE: content-format mancante dove serve (expect 4.00 o 4.06)"
OUT=$($COAP -m post "$BASE/factory/cmd" -v 8 -s "$TIMEOUT" 2>&1 || true)
if echo "$OUT" | egrep -q "4\.00|4\.06"; then
  pass "server strict su Content-Format (OK)"
else
  fail "atteso 4.00/4.06; output:"; echo "$OUT"
fi

echo
echo "==> Smoke test completato."
