#!/usr/bin/env bash
# One command: build, start Postgres + 2 instances + fake bank, run every e2e test, tear down.
# Exits non-zero on any failure. Safe to run twice in a row (compose volumes are removed).
set -uo pipefail
cd "$(dirname "$0")/.."

wait_for() {  # url expected-method [api-key]
  for _ in $(seq 1 120); do
    code=$(curl -s -o /dev/null -w '%{http_code}' -X "$2" ${3:+-H "X-API-Key: $3"} "$1" 2>/dev/null)
    [ "$code" = "200" ] && return 0
    sleep 1
  done
  echo "timed out waiting for $1" >&2
  return 1
}

./gradlew --no-daemon -q :bootJar :fake-bank:bootJar || exit 1
docker compose -f docker-compose.e2e.yaml down -v --remove-orphans >/dev/null 2>&1
docker compose -f docker-compose.e2e.yaml up -d || exit 1

rc=1
if wait_for http://localhost:9090/bank/admin/total-paid GET \
   && wait_for http://localhost:8081/mpesa/internal/reset POST e2e-admin-key \
   && wait_for http://localhost:8082/mpesa/internal/reset POST e2e-admin-key; then
  ./gradlew --no-daemon :e2e-tests:test
  rc=$?
fi
[ $rc -ne 0 ] && docker compose -f docker-compose.e2e.yaml logs --tail 40 wallet-1 wallet-2 fake-bank 2>/dev/null | tail -120
docker compose -f docker-compose.e2e.yaml down -v --remove-orphans >/dev/null 2>&1
exit $rc
