#!/usr/bin/env bash
set -euo pipefail

APP_LOG="${RUNNER_TEMP:-/tmp}/temporal-saga-e2e.log"
APP_PID=""

cleanup() {
  if [[ -n "$APP_PID" ]]; then kill "$APP_PID" 2>/dev/null || true; fi
  docker compose down -v --remove-orphans
}
trap cleanup EXIT

docker compose up -d --wait
mvn --batch-mode --no-transfer-progress spring-boot:run >"$APP_LOG" 2>&1 &
APP_PID=$!

ready=0
for _ in $(seq 1 120); do
  code="$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/api/orders || true)"
  if [[ "$code" == "405" ]]; then ready=1; break; fi
  if ! kill -0 "$APP_PID" 2>/dev/null; then cat "$APP_LOG"; exit 1; fi
  sleep 2
done
if [[ "$ready" != "1" ]]; then cat "$APP_LOG"; echo "Spring API did not become ready"; exit 1; fi

curl --fail-with-body -sS -X POST http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -d '{"orderId":"e2e-success","customerId":"customer-e2e","sku":"SKU-OK","quantity":1,"amount":49.90,"currency":"BRL"}'

curl --fail-with-body -sS -X POST http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -d '{"orderId":"e2e-failure","customerId":"customer-e2e","sku":"FAIL_DELIVERY","quantity":1,"amount":49.90,"currency":"BRL"}'

wait_for_log() {
  local pattern="$1"
  for _ in $(seq 1 90); do
    if grep -Fq "$pattern" "$APP_LOG"; then return 0; fi
    sleep 2
  done
  cat "$APP_LOG"
  echo "Expected Temporal activity event not found: $pattern"
  return 1
}

wait_for_log "reserve delivery orderId=e2e-success"
wait_for_log "sandbox refund orderId=e2e-failure"
wait_for_log "compensate inventory orderId=e2e-failure"

echo "Temporal end-to-end smoke test passed: success and reverse compensation observed."
