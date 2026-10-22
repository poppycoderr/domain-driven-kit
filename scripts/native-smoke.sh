#!/usr/bin/env bash
#
# Starts the native image of ddk-example-user and checks one request per capability that runs through DDK:
# repository reads and writes, conditional queries, paging, snowflake IDs, domain events after commit, business and
# validation errors in the web starter's format, and an MCP tool call. Exits non-zero on the first difference.
#
#   scripts/native-smoke.sh ddk-examples/ddk-example-user/target/ddk-example-user

set -euo pipefail

BINARY="${1:?usage: native-smoke.sh <native binary>}"
BASE="http://localhost:${PORT:-8080}"
LOG="$(mktemp)"
"$BINARY" --server.port="${PORT:-8080}" > "$LOG" 2>&1 &
APP=$!
trap 'kill "$APP" 2>/dev/null || true; rm -f "$LOG"' EXIT

for _ in $(seq 1 60); do
    grep -q "Started " "$LOG" && break
    kill -0 "$APP" 2>/dev/null || { cat "$LOG"; echo "the application exited during startup" >&2; exit 1; }
    sleep 1
done
grep -q "Started " "$LOG" || { cat "$LOG"; echo "the application did not start" >&2; exit 1; }

failed=0
# expect <name> <status> <text the body must contain> <curl arguments...>
expect() {
    local name="$1" status="$2" text="$3"
    shift 3
    local body code
    body="$(curl -s -w '\n%{http_code}' "$@")"
    code="${body##*$'\n'}"
    body="${body%$'\n'*}"
    if [[ "$code" == "$status" && "$body" == *"$text"* ]]; then
        printf 'ok   %s\n' "$name"
    else
        printf 'FAIL %s: expected %s with %s, got %s %s\n' "$name" "$status" "$text" "$code" "$body" >&2
        failed=1
    fi
}

JSON='Content-Type: application/json'
expect "register: conditional query, snowflake ID, insert" 200 '"username":"dave01"' \
    -X POST "$BASE/users" -H "$JSON" -d '{"username":"dave01","password":"password123","gender":1,"phoneNumber":"13900139001"}'
expect "get by ID, masked phone number" 200 '"phoneNumber":"138****8001"' "$BASE/users/1001"
expect "page with conditions" 200 '"records":[' \
    -X POST "$BASE/users/page" -H "$JSON" -d '{"enabled":true,"genders":[1],"pageNum":1,"pageSize":10}'
expect "update with optimistic lock" 200 '"email":"alice@corp.example"' \
    -X PUT "$BASE/users/1001" -H "$JSON" -d '{"email":"alice@corp.example"}'
expect "disable" 200 '"code":"SUCCESS"' -X PATCH "$BASE/users/1001/disable?reason=spam"
expect "delete" 200 '"code":"SUCCESS"' -X DELETE "$BASE/users/1002"
expect "business error" 400 '"code":"USERNAME_TAKEN"' \
    -X POST "$BASE/users" -H "$JSON" -d '{"username":"alice","password":"password123","gender":1,"phoneNumber":"13900139002"}'
expect "domain rule" 400 '"code":"INVALID_PHONE_NUMBER"' \
    -X POST "$BASE/users" -H "$JSON" -d '{"username":"erin01","password":"password123","gender":1,"phoneNumber":"123"}'
expect "not found" 400 '"code":"USER_NOT_FOUND"' "$BASE/users/9999"

MCP=(-H "$JSON" -H 'Accept: application/json, text/event-stream')
SESSION="$(curl -s -D - -o /dev/null -X POST "$BASE/mcp" "${MCP[@]}" \
    -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"native-smoke","version":"1"}}}' \
    | tr -d '\r' | awk -F': ' 'tolower($1) == "mcp-session-id" {print $2}')"
curl -s -o /dev/null -X POST "$BASE/mcp" "${MCP[@]}" -H "Mcp-Session-Id: $SESSION" -d '{"jsonrpc":"2.0","method":"notifications/initialized"}'
expect "MCP tool call" 200 'alice' -X POST "$BASE/mcp" "${MCP[@]}" -H "Mcp-Session-Id: $SESSION" \
    -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"get_user","arguments":{"id":1001}}}'

sleep 1
for line in "User registered: id=" "User disabled: id=1001"; do
    if grep -q "$line" "$LOG"; then
        printf 'ok   domain event after commit: %s\n' "$line"
    else
        printf 'FAIL domain event after commit: no "%s" in the log\n' "$line" >&2
        failed=1
    fi
done

if [[ "$failed" != 0 ]]; then
    echo "--- application log" >&2
    cat "$LOG" >&2
    exit 1
fi
echo "Native image smoke test passed"
