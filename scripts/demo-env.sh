# Demo helpers. From the repository root in Git Bash:  source scripts/demo-env.sh
# Reads the local test passwords from deployment/docker/.env (never committed) and never prints them.
# Git Bash would rewrite container paths such as /scripts/... into Windows paths; keep them as written.
export MSYS_NO_PATHCONV=1
export GW=http://localhost:8080
export TOKEN_URL=http://localhost:8180/realms/ecommerce-platform/protocol/openid-connect/token
export COMPOSE="docker compose -f deployment/docker/docker-compose.yml --env-file deployment/docker/.env"

env_value() { grep "^$1=" deployment/docker/.env | cut -d= -f2-; }

# Customer and admin access tokens. They expire after 5 minutes: run `tokens` again before each part of the demo.
tokens() {
  local get='import json,sys; print(json.load(sys.stdin)["access_token"])'
  CUSTOMER=$(curl -s "$TOKEN_URL" -d grant_type=password -d client_id=user-sign-in -d username=customer-test \
    --data-urlencode "password=$(env_value CUSTOMER_USER_PASSWORD)" | python -c "$get")
  ADMIN=$(curl -s "$TOKEN_URL" -d grant_type=password -d client_id=user-sign-in -d username=admin-test \
    --data-urlencode "password=$(env_value ADMIN_USER_PASSWORD)" | python -c "$get")
  export CUSTOMER ADMIN
  echo "tokens ready (customer ${#CUSTOMER} chars, admin ${#ADMIN} chars)"
}

# Pretty-prints a JSON response:  curl ... | pp
pp() { python -m json.tool; }

# Reads one field from a JSON response:  curl ... | field orderId
field() { python -c "import json,sys; print(json.load(sys.stdin)['$1'])"; }

# Waits until the whole order path works. An order far larger than the stock must come back 409 (out of stock):
# that needs Gateway -> order-service -> product-service and inventory-service, and creates nothing.
ready() {
  local code
  for _ in $(seq 1 60); do
    code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$GW/api/v1/orders" -H "Authorization: Bearer $CUSTOMER"       -H 'Content-Type: application/json' -d '{"items":[{"productId":2,"quantity":1000000}]}')
    [ "$code" = 409 ] && { echo "platform ready"; return 0; }
    sleep 5
  done
  echo "platform not ready (last answer $code)"; return 1
}

tokens
