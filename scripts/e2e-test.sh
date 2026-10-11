#!/usr/bin/env bash
# End-to-end acceptance test for the whole platform, run against the Docker Compose stack.
#
#   bash scripts/e2e-test.sh            # functional checks (about 2 minutes, changes no container)
#   bash scripts/e2e-test.sh --chaos    # also stops/restarts services: compensation, outages, DLT (about 6 minutes)
#
# Prerequisites: the full stack is up (docker compose ... up -d --build --wait), deployment/docker/.env exists,
# and curl + python (or python3) are on the PATH. Run from the repository root; Git Bash works on Windows.
# Every check prints PASS / FAIL / WARN with the requirement it proves; the exit code is the number of failures.
set -u
export MSYS_NO_PATHCONV=1
cd "$(dirname "$0")/.." || exit 1

CHAOS=false
[[ "${1:-}" == "--chaos" ]] && CHAOS=true

GW=${GW:-http://localhost:8080}
KC=${KC:-http://localhost:8180}
TOKEN_URL="$KC/realms/ecommerce-platform/protocol/openid-connect/token"
ENV_FILE=deployment/docker/.env
COMPOSE=(docker compose -f deployment/docker/docker-compose.yml --env-file "$ENV_FILE")
PY=$(command -v python3 || command -v python)
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

[[ -f "$ENV_FILE" ]] || { echo "Missing $ENV_FILE (copy .env.example and fill it in)"; exit 1; }
[[ -n "$PY" ]] || { echo "python or python3 is required"; exit 1; }

PASS=0; FAIL=0; WARN=0
pass() { PASS=$((PASS + 1)); printf '  \033[32mPASS\033[0m %s\n' "$1"; }
fail() { FAIL=$((FAIL + 1)); printf '  \033[31mFAIL\033[0m %s  (%s)\n' "$1" "$2"; }
warn() { WARN=$((WARN + 1)); printf '  \033[33mWARN\033[0m %s  (%s)\n' "$1" "$2"; }
section() { printf '\n\033[1m%s\033[0m\n' "$1"; }

env_value() { grep "^$1=" "$ENV_FILE" | cut -d= -f2-; }

# req METHOD URL [curl args...] -> sets CODE and BODY. Body and status come back on stdout (no temp file):
# on Windows, Git Bash may run curl.exe, which cannot see the MSYS /tmp folder.
req() {
  local method=$1 url=$2 out; shift 2
  out=$(curl -s -w '\n%{http_code}' -X "$method" "$url" "$@" | tr -d '\r')
  CODE=${out##*$'\n'}
  BODY=${out%$'\n'*}
  [[ "$BODY" == "$CODE" ]] && BODY=""
}
# status METHOD URL [curl args...] -> prints only the HTTP status (and curl's -w extras), discarding the body
status() { local method=$1 url=$2 out; shift 2; out=$(curl -s -w '\n%{http_code}' -X "$method" "$url" "$@" | tr -d '\r'); echo "${out##*$'\n'}"; }
# Python's stdout on Windows ends lines with \r\n; strip the \r so ids and statuses compare cleanly.
pyc() { "$PY" "$@" | tr -d '\r'; }
# jget EXPR -> evaluates a python expression on the parsed BODY (variable j); prints nothing on error
jget() { printf '%s' "$BODY" | pyc -c "import json,sys
try:
    j=json.load(sys.stdin); v=$1
    print('' if v is None else v)
except Exception:
    pass"; }
# check NAME EXPECTED_CODE_REGEX
check() { if [[ "$CODE" =~ ^($2)$ ]]; then pass "$1"; else fail "$1" "expected $2, got $CODE: ${BODY:0:160}"; fi; }
# assert NAME CONDITION_RESULT(true/false) DETAIL
assert() { if [[ "$2" == "true" ]]; then pass "$1"; else fail "$1" "$3"; fi; }

token_password() {
  curl -s "$TOKEN_URL" -d grant_type=password -d client_id=user-sign-in -d "username=$1" \
    --data-urlencode "password=$2" | pyc -c 'import json,sys; print(json.load(sys.stdin).get("access_token",""))'
}
token_client() {
  curl -s "$TOKEN_URL" -d grant_type=client_credentials -d "client_id=$1" \
    --data-urlencode "client_secret=$2" | pyc -c 'import json,sys; print(json.load(sys.stdin).get("access_token",""))'
}
tokens() {
  CUSTOMER=$(token_password customer-test "$(env_value CUSTOMER_USER_PASSWORD)")
  ADMIN=$(token_password admin-test "$(env_value ADMIN_USER_PASSWORD)")
}
psql_q() { "${COMPOSE[@]}" exec -T postgres psql -U postgres -d "$1" -tAc "$2" | tr -d '\r'; }
# Calls a business service from inside the Compose network (business ports are not published).
internal() { "${COMPOSE[@]}" exec -T order-service curl -s -o /dev/null -w '%{http_code}' "$@" | tr -d '\r'; }

place_order() { # place_order PRODUCT_ID QUANTITY -> sets ORDER (empty on failure)
  req POST "$GW/api/v1/orders" -H "Authorization: Bearer $CUSTOMER" -H 'Content-Type: application/json' \
    -d "{\"items\":[{\"productId\":$1,\"quantity\":$2}]}"
  ORDER=$(jget "j['orderId']")
}
order_status() { req GET "$GW/api/v1/orders/$1" -H "Authorization: Bearer $CUSTOMER"; jget "j['status']"; }
wait_status() { # wait_status ORDER_ID STATUS SECONDS -> prints the last status seen
  local status="" i
  for ((i = 0; i < $3; i++)); do
    status=$(order_status "$1"); [[ "$status" == "$2" ]] && break; sleep 1
  done
  echo "$status"
}
stock() { req GET "$GW/api/v1/inventory/$1" -H "Authorization: Bearer $ADMIN"; jget "str(j['available'])+' '+str(j['reserved'])"; }
ready() { # the whole order path answers: an impossible quantity must come back 409, which needs Gateway, Order, Product and Inventory
  local i
  for ((i = 0; i < 60; i++)); do
    tokens
    req POST "$GW/api/v1/orders" -H "Authorization: Bearer $CUSTOMER" -H 'Content-Type: application/json' \
      -d '{"items":[{"productId":2,"quantity":1000000}]}'
    [[ "$CODE" == 409 ]] && return 0
    sleep 5
  done
  return 1
}
notification_lines() { "${COMPOSE[@]}" logs --no-color notification-service 2>/dev/null | grep "$1" | grep -c "$2"; }

# ------------------------------------------------------------------------------------------------ 0. preflight
section "0. Platform is up (L0, NFR-08)"
unhealthy=$("${COMPOSE[@]}" ps --all --format '{{.Service}} {{.Health}}' | grep -v ' healthy$' | grep -v '^swagger-ui' || true)
assert "every Compose container reports healthy" "$([[ -z "$unhealthy" ]] && echo true || echo false)" "not healthy: $unhealthy"
if ready; then pass "order path ready (Gateway -> Order -> Product + Inventory)"; else fail "order path ready" "no 409 after 5 minutes"; fi
cfg=$("${COMPOSE[@]}" exec -T eureka-server curl -s http://config-server:8888/application/default | tr -d '\r')
assert "Config Server serves application/default from config-repo" "$([[ "$cfg" == *'"name":"application"'* ]] && echo true || echo false)" "${cfg:0:120}"
apps=$("${COMPOSE[@]}" exec -T eureka-server curl -s -H 'Accept: application/json' http://localhost:8761/eureka/apps | tr -d '\r' |
  pyc -c 'import json,sys; print(" ".join(sorted(a["name"] for a in json.load(sys.stdin)["applications"]["application"])))' 2>/dev/null)
for app in API-GATEWAY PRODUCT-SERVICE ORDER-SERVICE PAYMENT-SERVICE INVENTORY-SERVICE NOTIFICATION-SERVICE REVIEW-SERVICE; do
  assert "Eureka has $app registered" "$([[ " $apps " == *" $app "* ]] && echo true || echo false)" "registered: $apps"
done

# ------------------------------------------------------------------------------------------------ 1. L1
section "1. Catalogue, security, cache (L1: FR-01..FR-04, FR-13, FR-15)"
req GET "$GW/api/v1/products?page=0&size=5"
check "FR-01 anonymous browse is public and paginated" 200
assert "FR-01 page has products and page metadata" "$(jget "str(len(j['content'])>0 and 'totalElements' in j['page']).lower()")" "${BODY:0:160}"
req GET "$GW/api/v1/products/1"
check "FR-03 product detail" 200
assert "FR-03 detail carries categoryName (read model)" "$(jget "str(bool(j['categoryName'])).lower()")" "${BODY:0:160}"
req GET "$GW/api/v1/products/999999"
check "unknown product answers 404" 404

req GET "$GW/api/v1/orders"
check "FR-04 protected route without token -> 401" 401
req GET "$GW/api/v1/orders" -H 'Authorization: Bearer not-a-real-token'
check "FR-04 invalid token -> 401" 401
tokens
assert "Keycloak issues customer and admin tokens" "$([[ -n "$CUSTOMER" && -n "$ADMIN" ]] && echo true || echo false)" "check the passwords in $ENV_FILE"

PRODUCT_BODY='{"name":"E2E Test Lamp","price":25.00,"categoryId":2}'
req POST "$GW/api/v1/products" -H 'Content-Type: application/json' -d "$PRODUCT_BODY"
check "FR-02 product write without token -> 401" 401
req POST "$GW/api/v1/products" -H "Authorization: Bearer $CUSTOMER" -H 'Content-Type: application/json' -d "$PRODUCT_BODY"
check "FR-02 product write as CUSTOMER -> 403" 403
req POST "$GW/api/v1/products" -H "Authorization: Bearer $CUSTOMER" -H 'X-User-Roles: ADMIN' -H 'X-User-Id: attacker' \
  -H 'Content-Type: application/json' -d "$PRODUCT_BODY"
check "Gateway strips forged X-User-* headers (CUSTOMER + forged ADMIN header -> 403)" 403
req POST "$GW/api/v1/products" -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' -d '{"name":"","price":-1,"categoryId":2}'
check "invalid product body -> 400" 400
req POST "$GW/api/v1/products" -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' -d "$PRODUCT_BODY"
check "FR-02 ADMIN creates a product -> 201" 201
LAMP=$(jget "j['id']")

req GET "$GW/api/v1/products/$LAMP" >/dev/null
keys=$("${COMPOSE[@]}" exec -T redis redis-cli --scan --pattern 'products*' | tr -d '\r')
assert "FR-15 product read is cached in Redis (products:item::$LAMP)" "$([[ "$keys" == *"products:item::$LAMP"* ]] && echo true || echo false)" "keys: $(echo $keys)"
req PUT "$GW/api/v1/products/$LAMP" -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
  -d '{"name":"E2E Test Lamp v2","price":26.00,"categoryId":2}'
check "FR-02 ADMIN updates the product -> 200" 200
sleep 1
keys=$("${COMPOSE[@]}" exec -T redis redis-cli --scan --pattern 'products*' | tr -d '\r')
assert "FR-15 write evicts the item and every list page" "$([[ "$keys" != *"products:item::$LAMP"* && "$keys" != *"products:list"* ]] && echo true || echo false)" "keys left: $(echo $keys)"
req GET "$GW/api/v1/products/$LAMP"
assert "FR-15 next read shows the new value (no stale cache)" "$([[ "$(jget "j['name']")" == "E2E Test Lamp v2" ]] && echo true || echo false)" "${BODY:0:120}"

# FR-13: one anonymous client floods; a signed-in client (keyed by JWT subject) must still be served.
if docker image inspect grafana/k6:1.3.0 >/dev/null 2>&1; then
  net=$(docker inspect -f '{{range $k, $v := .NetworkSettings.Networks}}{{$k}}{{end}}' "$("${COMPOSE[@]}" ps -q api-gateway)")
  host_k6="$(pwd -W 2>/dev/null || pwd)/k6"
  docker run --rm --network "$net" -v "$host_k6:/scripts" -e BASE_URL=http://api-gateway:8080 grafana/k6:1.3.0 \
    run --quiet --summary-export /scripts/.rate-limit-summary.json /scripts/rate-limit-demo.js >/dev/null 2>&1
  limited=$(pyc -c 'import json,sys
c=json.load(open(sys.argv[1]))["root_group"]["checks"]
c=c.values() if isinstance(c,dict) else c
print(sum(x["passes"] for x in c if "429" in x["name"]))' k6/.rate-limit-summary.json 2>/dev/null || echo 0)
  rm -f k6/.rate-limit-summary.json
else
  seq 1 600 | xargs -P 100 -I{} curl -s -w '\n%{http_code}\n' "$GW/api/v1/products/1" | tr -d '\r' > "$TMP/codes"
  limited=$(grep -c '^429$' "$TMP/codes")
fi
if [[ "${limited:-0}" -gt 0 ]]; then pass "FR-13 flooding anonymous client gets 429 (Redis token bucket)"
else warn "FR-13 rate limit" "no 429 seen: this machine could not send faster than 100 req/s; use k6/rate-limit-demo.js"; fi
req GET "$GW/api/v1/orders" -H "Authorization: Bearer $CUSTOMER"
check "FR-13 another client (signed in, keyed by subject) is still served" 200
sleep 3   # let the anonymous bucket refill before the next section

# ------------------------------------------------------------------------------------------------ 2. L2
section "2. Inventory, payment, place order (L2: FR-05, FR-06, FR-08, FR-10, FR-12, FR-14)"
tokens
req GET "$GW/api/v1/inventory/1" -H "Authorization: Bearer $ADMIN"
check "FR-12 ADMIN views stock" 200
req GET "$GW/api/v1/inventory/1" -H "Authorization: Bearer $CUSTOMER"
check "FR-12 CUSTOMER cannot view stock -> 403" 403
req PUT "$GW/api/v1/inventory/19" -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' -d '{"available":-1}'
check "FR-12 negative stock adjustment -> 400" 400
req PUT "$GW/api/v1/inventory/19" -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' -d '{"available":1000}'
check "FR-12 ADMIN adjusts stock" 200
req GET "$GW/api/v1/inventory/low-stock?threshold=10" -H "Authorization: Bearer $ADMIN"
check "low-stock query" 200
assert "low-stock lists product 20 (seeded with 0)" "$(jget "str(any(r['productId']==20 for r in j)).lower()")" "${BODY:0:160}"
req GET "$GW/api/v1/inventory/check?productId=1&quantity=1" -H "Authorization: Bearer $ADMIN"
assert "internal stock check has no public Gateway route" "$([[ "$CODE" =~ ^(401|403|404)$ ]] && echo true || echo false)" "got $CODE"

req POST "$GW/api/v1/orders" -H "Authorization: Bearer $CUSTOMER" -H 'Content-Type: application/json' -d '{"items":[]}'
check "order without items -> 400" 400
req POST "$GW/api/v1/orders" -H "Authorization: Bearer $CUSTOMER" -H 'Content-Type: application/json' -d '{"items":[{"productId":999999,"quantity":1}]}'
check "order for an unknown product -> 400" 400
req POST "$GW/api/v1/orders" -H "Authorization: Bearer $CUSTOMER" -H 'Content-Type: application/json' -d '{"items":[{"productId":20,"quantity":1}]}'
check "FR-06 out-of-stock order rejected before payment -> 409" 409
req POST "$GW/api/v1/orders" -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' -d '{"items":[{"productId":1,"quantity":1}]}'
check "ADMIN without CUSTOMER role cannot order -> 403" 403

req GET "$GW/api/v1/products/1"; PRICE1=$(jget "float(j['price'])")
before=$(stock 1)
place_order 1 1
check "FR-05 place order -> 201" 201
assert "FR-05 answer is immediate with orderId and PENDING" "$([[ -n "$ORDER" && "$(jget "j['status']")" == PENDING ]] && echo true || echo false)" "${BODY:0:160}"
FIRST_ORDER=$ORDER
req GET "$GW/api/v1/orders/$ORDER" -H "Authorization: Bearer $CUSTOMER"
check "FR-10 customer reads own order by id" 200
assert "order freezes the price read from Product (unitPrice = catalogue price)" "$(jget "str(float(j['items'][0]['unitPrice'])==$PRICE1).lower()")" "price $PRICE1, ${BODY:0:200}"
req GET "$GW/api/v1/orders?page=0&size=50" -H "Authorization: Bearer $CUSTOMER"
assert "FR-10 own-orders list contains the new order" "$(jget "str(any(o['orderId']=='$ORDER' for o in j['content'])).lower()")" "${BODY:0:160}"
req GET "$GW/api/v1/orders/00000000-0000-0000-0000-000000000000" -H "Authorization: Bearer $CUSTOMER"
check "FR-10 an order that is not yours (or does not exist) -> 404" 404

# Service-to-service rules (FR-14, ADD §7) — called inside the Compose network.
OP=$(token_client payment-operator "$(env_value PAYMENT_OPERATOR_CLIENT_SECRET)")
OS=$(token_client order-service "$(env_value ORDER_CLIENT_SECRET)")
assert "Keycloak issues Client Credentials tokens (order-service, payment-operator)" "$([[ -n "$OP" && -n "$OS" ]] && echo true || echo false)" "check the client secrets in $ENV_FILE"
code=$(internal http://product-service:8081/api/v1/products)
assert "direct call to product-service without token -> 401" "$([[ "$code" == 401 ]] && echo true || echo false)" "got $code"
code=$(internal -H "Authorization: Bearer $CUSTOMER" http://product-service:8081/api/v1/products)
assert "a user token cannot call a service directly (wrong audience/client)" "$([[ "$code" =~ ^(401|403)$ ]] && echo true || echo false)" "got $code"
code=$(internal -H "Authorization: Bearer $OS" "http://inventory-service:8084/api/v1/inventory/check?productId=1&quantity=1")
assert "FR-14 order-service token may call the stock check" "$([[ "$code" == 200 ]] && echo true || echo false)" "got $code"
code=$(internal -H "Authorization: Bearer $OP" "http://inventory-service:8084/api/v1/inventory/check?productId=1&quantity=1")
assert "payment-operator token is refused on the stock check" "$([[ "$code" =~ ^(401|403)$ ]] && echo true || echo false)" "got $code"
code=$(internal -X PUT -H "Authorization: Bearer $OS" -H 'X-User-Roles: ADMIN' -H 'Content-Type: application/json' \
  -d '{"available":5}' http://inventory-service:8084/api/v1/inventory/1)
assert "order-service token + forged ADMIN header cannot adjust stock" "$([[ "$code" =~ ^(401|403)$ ]] && echo true || echo false)" "got $code"

PAY_ORDER=$(pyc -c 'import uuid; print(uuid.uuid4())')
pay() { "${COMPOSE[@]}" exec -T order-service curl -s -w '\n%{http_code}' -X POST http://payment-service:8083/api/v1/payments \
  -H "Authorization: Bearer $1" -H 'Content-Type: application/json' "${@:3}" -d "$2" | tr -d '\r'; }
out=$(pay "$OP" "{\"orderId\":\"$PAY_ORDER\",\"amount\":10.00}")
assert "payment without Idempotency-Key -> 400" "$([[ "${out##*$'\n'}" == 400 ]] && echo true || echo false)" "$out"
out=$(pay "$OS" "{\"orderId\":\"$PAY_ORDER\",\"amount\":10.00}" -H 'Idempotency-Key: e2e-key-1')
assert "order-service token is refused on the Payment API" "$([[ "${out##*$'\n'}" =~ ^(401|403)$ ]] && echo true || echo false)" "$out"
out=$(pay "$OP" "{\"orderId\":\"$PAY_ORDER\",\"amount\":10.00}" -H "Idempotency-Key: e2e-$PAY_ORDER")
first_code=${out##*$'\n'}; BODY=${out%$'\n'*}; PAYMENT_ID=$(jget "j['paymentId']")
assert "FR-08 first payment -> 201 COMPLETED" "$([[ "$first_code" == 201 && "$(jget "j['status']")" == COMPLETED ]] && echo true || echo false)" "$out"
out=$(pay "$OP" "{\"orderId\":\"$PAY_ORDER\",\"amount\":10.00}" -H "Idempotency-Key: e2e-$PAY_ORDER")
BODY=${out%$'\n'*}
assert "FR-08 same key again -> 200 with the SAME paymentId (charged once)" "$([[ "${out##*$'\n'}" == 200 && "$(jget "j['paymentId']")" == "$PAYMENT_ID" ]] && echo true || echo false)" "$out"
out=$(pay "$OP" "{\"orderId\":\"$PAY_ORDER\",\"amount\":99.00}" -H "Idempotency-Key: e2e-$PAY_ORDER")
assert "same key with a different body -> 422" "$([[ "${out##*$'\n'}" == 422 ]] && echo true || echo false)" "$out"
rows=$(psql_q payment_db "SELECT count(*) FROM payment WHERE order_id='$PAY_ORDER'")
assert "FR-08 exactly one payment row in payment_db" "$([[ "$rows" == 1 ]] && echo true || echo false)" "rows=$rows"
code=$(internal -X POST -H "Authorization: Bearer $OP" "http://payment-service:8083/api/v1/payments/$PAYMENT_ID/refund")
assert "refund a completed payment -> 200" "$([[ "$code" == 200 ]] && echo true || echo false)" "got $code"
code=$(internal -X POST -H "Authorization: Bearer $OP" "http://payment-service:8083/api/v1/payments/$PAYMENT_ID/refund")
assert "refund twice -> 409" "$([[ "$code" == 409 ]] && echo true || echo false)" "got $code"

# ------------------------------------------------------------------------------------------------ 3. L3 + L5
section "3. Saga, outbox, notification, tracing (L3/L5: FR-07, FR-09, FR-11, NFR-06, NFR-10)"
status=$(wait_status "$FIRST_ORDER" CONFIRMED 30)
assert "FR-09 happy path: order reaches CONFIRMED" "$([[ "$status" == CONFIRMED ]] && echo true || echo false)" "last status $status"
after=$(stock 1)
read -r a0 r0 <<<"$before"; read -r a1 r1 <<<"$after"
assert "FR-07 stock reserved for the order (available -1, reserved +1)" "$([[ $((a0 - a1)) == 1 && $((r1 - r0)) == 1 ]] && echo true || echo false)" "before '$before' after '$after'"
unpublished=$(psql_q order_db "SELECT count(*) FROM outbox_event WHERE aggregate_id='$FIRST_ORDER' AND published_at IS NULL")
assert "outbox rows for the order were published" "$([[ "$unpublished" == 0 ]] && echo true || echo false)" "unpublished=$unpublished"
for ((i = 0; i < 10; i++)); do n=$(notification_lines "$FIRST_ORDER" NOTIFICATION); [[ "$n" -ge 1 ]] && break; sleep 1; done
assert "FR-11 customer notified of the confirmation" "$([[ "$n" == 1 ]] && echo true || echo false)" "notification lines: $n"

# NFR-10: deliver the same PaymentCompleted again; nothing may change.
event=$(psql_q payment_db "SELECT payload::text FROM outbox_event WHERE aggregate_id='$FIRST_ORDER' AND event_type='PaymentCompleted'")
if [[ -n "$event" ]]; then
  printf '%s|%s\n' "$FIRST_ORDER" "$event" | "${COMPOSE[@]}" exec -T kafka kafka-console-producer --bootstrap-server kafka:29092 \
    --topic payment-events --property parse.key=true --property 'key.separator=|' >/dev/null 2>&1
  sleep 5
  confirmed=$(psql_q order_db "SELECT count(*) FROM outbox_event WHERE aggregate_id='$FIRST_ORDER' AND event_type='OrderConfirmed'")
  n=$(notification_lines "$FIRST_ORDER" NOTIFICATION)
  assert "NFR-10 duplicate PaymentCompleted is ignored (one OrderConfirmed, one notice)" "$([[ "$confirmed" == 1 && "$n" == 1 ]] && echo true || echo false)" "OrderConfirmed rows=$confirmed notices=$n"
else
  fail "NFR-10 duplicate delivery" "no PaymentCompleted outbox row found for $FIRST_ORDER"
fi

trace=$(psql_q order_db "SELECT split_part(trace_parent,'-',2) FROM outbox_event WHERE aggregate_id='$FIRST_ORDER' AND event_type='OrderPlaced'")
services=""
for ((i = 0; i < 15; i++)); do
  services=$(curl -s "http://localhost:9411/api/v2/trace/$trace" | pyc -c 'import json,sys
print(" ".join(sorted({s.get("localEndpoint",{}).get("serviceName","") for s in json.load(sys.stdin)})))' 2>/dev/null)
  [[ "$services" == *payment-service* && "$services" == *inventory-service* ]] && break; sleep 2
done
assert "NFR-06 one trace spans HTTP and Kafka (gateway, order, inventory, payment)" "$([[ "$services" == *api-gateway* && "$services" == *order-service* && "$services" == *inventory-service* && "$services" == *payment-service* ]] && echo true || echo false)" "services in trace $trace: $services"
line=$("${COMPOSE[@]}" logs --no-color notification-service 2>/dev/null | grep "$FIRST_ORDER" | grep -m1 NOTIFICATION || true)
assert "NFR-06 notification log line is JSON and carries the trace id" "$([[ "$line" == *'"trace.id"'* || "$line" == *'"traceId"'* || "$line" == *'"trace_id"'* ]] && echo true || echo false)" "${line:0:160}"

# ------------------------------------------------------------------------------------------------ 4. L6 B1
section "4. Reviews and ratings (L6: FR-16, Bonus B1)"
tokens
REVIEW='{"rating":5,"text":"E2E: bright and warm light."}'
req POST "$GW/api/v1/products/$LAMP/reviews" -H 'Content-Type: application/json' -d "$REVIEW"
check "review without token -> 401" 401
req POST "$GW/api/v1/products/$LAMP/reviews" -H "Authorization: Bearer $CUSTOMER" -H 'Content-Type: application/json' -d '{"rating":6,"text":"too high"}'
check "rating outside 1..5 -> 400" 400
req POST "$GW/api/v1/products/$LAMP/reviews" -H "Authorization: Bearer $CUSTOMER" -H 'Content-Type: application/json' -d "$REVIEW"
check "B1 customer submits a review -> 201" 201
req POST "$GW/api/v1/products/$LAMP/reviews" -H "Authorization: Bearer $CUSTOMER" -H 'Content-Type: application/json' -d "$REVIEW"
check "B1 second review by the same customer -> 409" 409
req GET "$GW/api/v1/products/$LAMP/reviews?page=0&size=10"
check "B1 reviews are public" 200
assert "B1 the review is listed" "$(jget "str(j['page']['totalElements']==1).lower()")" "${BODY:0:160}"
for ((i = 0; i < 15; i++)); do
  req GET "$GW/api/v1/products/$LAMP"; [[ "$(jget "j['reviewCount']")" == 1 ]] && break; sleep 1
done
assert "B1 product detail shows reviewCount 1 and averageRating 5 (ReviewSubmitted projection)" "$(jget "str(j['reviewCount']==1 and float(j['averageRating'])==5.0).lower()")" "${BODY:0:200}"

# ------------------------------------------------------------------------------------------------ 5. observability
section "5. Observability (L5)"
targets=$(curl -s http://localhost:9090/api/v1/targets | pyc -c 'import json,sys
t=json.load(sys.stdin)["data"]["activeTargets"]; print(sum(x["health"]=="up" for x in t), len(t))' 2>/dev/null)
read -r up total <<<"${targets:-0 0}"
assert "Prometheus scrapes every target (up $up of $total)" "$([[ "$total" -gt 0 && "$up" == "$total" ]] && echo true || echo false)" "up $up of $total"
code=$(status GET http://localhost:3000/api/health)
assert "Grafana is up" "$([[ "$code" == 200 ]] && echo true || echo false)" "got $code"
if "${COMPOSE[@]}" ps --format '{{.Service}}' | grep -qx storefront; then
  code=$(status GET http://localhost:8070/)
  assert "Storefront page is served on :8070" "$([[ "$code" == 200 ]] && echo true || echo false)" "got $code"
  code=$(status GET "http://localhost:8070/api/v1/products?page=0&size=1")
  assert "Storefront proxies /api to the Gateway" "$([[ "$code" == 200 ]] && echo true || echo false)" "got $code"
  code=$(status GET http://localhost:8070/auth/realms/ecommerce-platform)
  assert "Storefront proxies /auth to Keycloak" "$([[ "$code" == 200 ]] && echo true || echo false)" "got $code"
fi

# ------------------------------------------------------------------------------------------------ 6. chaos
if $CHAOS; then
  section "6. Failure paths (chaos: NFR-01, NFR-05, FR-09 compensation, F4 circuit breaker, FR-11 DLT)"

  echo "  ... restarting payment-service with PAYMENT_FAILURE_RATE=1.0"
  PAYMENT_FAILURE_RATE=1.0 "${COMPOSE[@]}" up -d --wait payment-service >/dev/null 2>&1
  tokens; before=$(stock 3)
  place_order 3 1
  status=$(wait_status "$ORDER" CANCELLED 60)
  assert "FR-09 payment declined -> order CANCELLED" "$([[ "$status" == CANCELLED ]] && echo true || echo false)" "last status $status"
  sleep 2; after=$(stock 3)
  assert "FR-09 compensation released the stock (same numbers as before)" "$([[ "$before" == "$after" ]] && echo true || echo false)" "before '$before' after '$after'"
  held=$(psql_q inventory_db "SELECT count(*) FROM reservation WHERE order_id='$ORDER' AND status='RESERVED'")
  assert "NFR-05 no reservation left RESERVED after cancel" "$([[ "$held" == 0 ]] && echo true || echo false)" "RESERVED rows=$held"
  sleep 2; n=$(notification_lines "$ORDER" NOTIFICATION)
  assert "FR-11 customer notified of the cancellation" "$([[ "$n" -ge 1 ]] && echo true || echo false)" "notification lines: $n"
  "${COMPOSE[@]}" up -d --wait payment-service >/dev/null 2>&1

  echo "  ... stopping payment-service (outage)"
  "${COMPOSE[@]}" stop payment-service >/dev/null 2>&1
  tokens; place_order 1 1; OUTAGE_ORDER=$ORDER
  sleep 15
  status=$(order_status "$OUTAGE_ORDER")
  assert "NFR-01 Payment down: order stays PENDING (not lost, not failed)" "$([[ "$status" == PENDING ]] && echo true || echo false)" "status $status"
  "${COMPOSE[@]}" start payment-service >/dev/null 2>&1
  status=$(wait_status "$OUTAGE_ORDER" CONFIRMED 120)
  assert "NFR-01 after Payment restarts the order completes (CONFIRMED)" "$([[ "$status" == CONFIRMED ]] && echo true || echo false)" "last status $status"

  echo "  ... stopping inventory-service (circuit breaker)"
  "${COMPOSE[@]}" stop inventory-service >/dev/null 2>&1
  tokens; codes=""; last=""
  for i in 1 2 3 4 5 6; do
    out=$(curl -s -w '\n%{http_code} %{time_total}' -X POST "$GW/api/v1/orders" -H "Authorization: Bearer $CUSTOMER" \
      -H 'Content-Type: application/json' -d '{"items":[{"productId":1,"quantity":1}]}' | tr -d '\r')
    out=${out##*$'\n'}; codes="$codes ${out%% *}"; last=${out##* }
  done
  assert "F4 Inventory down -> every order answers 503, none created" "$([[ "$codes" == " 503 503 503 503 503 503" ]] && echo true || echo false)" "codes:$codes"
  assert "F4 circuit open -> fails fast (last call ${last}s < 0.5s)" "$(pyc -c "print(str($last < 0.5).lower())")" "last call took ${last}s"
  "${COMPOSE[@]}" start inventory-service >/dev/null 2>&1
  ready || true

  echo "  ... restarting notification-service with NOTIFICATION_FAIL=true"
  NOTIFICATION_FAIL=true "${COMPOSE[@]}" up -d --wait notification-service >/dev/null 2>&1
  tokens; place_order 1 1; DLT_ORDER=$ORDER
  wait_status "$DLT_ORDER" CONFIRMED 30 >/dev/null
  for ((i = 0; i < 60; i++)); do n=$(notification_lines "$DLT_ORDER" 'order-events.DLT'); [[ "$n" -ge 1 ]] && break; sleep 1; done
  assert "FR-11 failing notice is retried, then parked on order-events.DLT (ALERT log)" "$([[ "$n" -ge 1 ]] && echo true || echo false)" "no DLT alert for $DLT_ORDER after 60 s"
  "${COMPOSE[@]}" up -d --wait notification-service >/dev/null 2>&1
fi

# ------------------------------------------------------------------------------------------------ cleanup + summary
tokens
req DELETE "$GW/api/v1/products/$LAMP" -H "Authorization: Bearer $ADMIN"
check "FR-02 ADMIN deletes the test product -> 204" 204
req GET "$GW/api/v1/products/$LAMP"
check "deleted product is gone (cache evicted) -> 404" 404

printf '\n\033[1mResult: %d passed, %d failed, %d warnings\033[0m\n' "$PASS" "$FAIL" "$WARN"
$CHAOS || echo "Run with --chaos to also test compensation, the Payment outage, the circuit breaker and the DLT."
exit "$FAIL"
