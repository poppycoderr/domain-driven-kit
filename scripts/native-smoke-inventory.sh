#!/usr/bin/env bash
#
# Starts the native image of ddk-example-inventory against a running Redis and checks one request per capability that
# runs through DDK: the two-level cache with eviction, an aggregate lock with duplicate-submit protection, a rate limit,
# a projection refreshed after commit, and a listener run through Spring Modulith's publication registry with an
# idempotent consumer. A second run with the tenant profile checks that request ids and rate limit quotas are kept per
# sales channel (the tenant) while the stock stays shared. Exits non-zero after reporting every difference.
#
#   scripts/native-smoke-inventory.sh ddk-examples/ddk-example-inventory/target/ddk-example-inventory
#
# Redis is expected at REDIS_HOST:REDIS_PORT (default localhost:6379) and should be empty.

set -euo pipefail

BINARY="${1:?usage: native-smoke-inventory.sh <native binary>}"
BASE="http://localhost:${PORT:-8080}"
LOG="$(mktemp)"
APP=""
trap 'kill "$APP" 2>/dev/null || true; rm -f "$LOG"' EXIT

# start <arguments...>: starts the binary and waits until it has started
start() {
    : > "$LOG"
    "$BINARY" --server.port="${PORT:-8080}" "$@" > "$LOG" 2>&1 &
    APP=$!
    for _ in $(seq 1 60); do
        grep -q "Started " "$LOG" && return
        kill -0 "$APP" 2>/dev/null || { cat "$LOG"; echo "the application exited during startup" >&2; exit 1; }
        sleep 1
    done
    cat "$LOG"
    echo "the application did not start" >&2
    exit 1
}
# stop: stops the binary, then fails the test if it did not stop within 30 seconds or logged an ERROR
stop() {
    kill "$APP" 2>/dev/null || true
    for _ in $(seq 1 60); do
        kill -0 "$APP" 2>/dev/null || break
        sleep 0.5
    done
    local errors=0
    if kill -0 "$APP" 2>/dev/null; then
        echo "FAIL the application did not stop within 30 seconds of SIGTERM" >&2
        kill -9 "$APP" 2>/dev/null || true
        errors=1
    fi
    wait "$APP" 2>/dev/null || true
    if grep -E ' ERROR ' "$LOG" >&2; then
        echo "FAIL errors in the application log" >&2
        errors=1
    fi
    if [[ "$errors" != 0 ]]; then
        echo "--- application log" >&2
        cat "$LOG" >&2
        failed=1
    fi
}

failed=0
# expect <name> <status> <text the body must contain> <curl arguments...>
expect() {
    local name="$1" status="$2" text="$3"
    shift 3
    local body code
    body="$(curl -s -m 10 -w '\n%{http_code}' "$@")"
    code="${body##*$'\n'}"
    body="${body%$'\n'*}"
    if [[ "$code" == "$status" && "$body" == *"$text"* ]]; then
        printf 'ok   %s\n' "$name"
    else
        printf 'FAIL %s: expected %s with %s, got %s %s\n' "$name" "$status" "$text" "$code" "$body" >&2
        failed=1
    fi
}
# eventually <name> <text> <url>: polls for up to 20 seconds
eventually() {
    local name="$1" text="$2" url="$3"
    for _ in $(seq 1 40); do
        [[ "$(curl -s -m 10 "$url")" == *"$text"* ]] && { printf 'ok   %s\n' "$name"; return; }
        sleep 0.5
    done
    printf 'FAIL %s: no %s from %s within 20 seconds\n' "$name" "$text" "$url" >&2
    failed=1
}

JSON='Content-Type: application/json'
start
expect "cache: first read" 200 '"available":10' "$BASE/skus/1"
expect "cache: second read from the cache" 200 '"name":"apple"' "$BASE/skus/1"
expect "lock and idempotency: reserve" 200 '"available":4' \
    -X POST "$BASE/skus/1/reservations" -H "$JSON" -d '{"requestId":"r-1","quantity":6}'
expect "idempotency: the same request again" 409 '"code":"DUPLICATE_REQUEST"' \
    -X POST "$BASE/skus/1/reservations" -H "$JSON" -d '{"requestId":"r-1","quantity":6}'
expect "domain rule: out of stock" 400 '"code":"OUT_OF_STOCK"' \
    -X POST "$BASE/skus/1/reservations" -H "$JSON" -d '{"requestId":"r-2","quantity":5}'
expect "request validation" 400 '"code":"VALIDATION_ERROR"' \
    -X POST "$BASE/skus/1/reservations" -H "$JSON" -d '{"requestId":"r-3","quantity":0}'
expect "cache: evicted by the reservation" 200 '"available":4' "$BASE/skus/1"
for i in 1 2 3; do
    expect "rate limit: check $i of 3" 200 '"available":8' "$BASE/skus/2/availability"
done
expect "rate limit: the fourth check" 429 '"code":"RATE_LIMITED"' "$BASE/skus/2/availability"
eventually "projection: refreshed after commit" '"skuId":"1"' "$BASE/low-stock"
eventually "publication registry and idempotent consumer: listener ran" '"r-1"' "$BASE/skus/1/reservations"

stop

# The tenant profile starts with a fresh in-memory database, so sku 1 has 10 again. Its cached entry from the first run
# is still in Redis, so this run checks stock only through reservations, which load from the database
start --spring.profiles.active=tenant
WEB='X-Sales-Channel: web'
APP_CHANNEL='X-Sales-Channel: app'
expect "tenant: web reserves r-1" 200 '"available":9' \
    -X POST "$BASE/skus/1/reservations" -H "$JSON" -H "$WEB" -d '{"requestId":"r-1","quantity":1}'
expect "tenant: app reserves its own r-1" 200 '"available":8' \
    -X POST "$BASE/skus/1/reservations" -H "$JSON" -H "$APP_CHANNEL" -d '{"requestId":"r-1","quantity":1}'
expect "tenant: web repeats r-1" 409 '"code":"DUPLICATE_REQUEST"' \
    -X POST "$BASE/skus/1/reservations" -H "$JSON" -H "$WEB" -d '{"requestId":"r-1","quantity":1}'
for i in 1 2 3; do
    expect "tenant: web availability check $i of 3" 200 '"available":8' -H "$WEB" "$BASE/skus/2/availability"
done
expect "tenant: web's fourth check" 429 '"code":"RATE_LIMITED"' -H "$WEB" "$BASE/skus/2/availability"
expect "tenant: app has its own quota" 200 '"available":8' -H "$APP_CHANNEL" "$BASE/skus/2/availability"
stop

if [[ "$failed" != 0 ]]; then
    exit 1
fi
echo "Native image smoke test passed"
