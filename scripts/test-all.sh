#!/usr/bin/env bash
# Runs every test level of the project in order and stops at the first failing level.
#
#   bash scripts/test-all.sh            # 1 unit + integration, 2 start stack, 3 end-to-end, 4 k6 smoke
#   bash scripts/test-all.sh --chaos    # same, and the end-to-end step also runs the failure paths
#
# Needs: Java 21, Maven, Docker (Testcontainers and Compose), curl, python. Run from the repository root.
set -euo pipefail
export MSYS_NO_PATHCONV=1
cd "$(dirname "$0")/.." || exit 1
COMPOSE=(docker compose -f deployment/docker/docker-compose.yml --env-file deployment/docker/.env)
step() { printf '\n\033[1m==> %s\033[0m\n' "$1"; }

step "1/4 Unit, web-slice and Testcontainers integration tests + 60% coverage gate (mvn -B verify)"
mvn -B verify

step "2/4 Build images and start the whole platform (waits until every container is healthy)"
"${COMPOSE[@]}" up -d --build --wait

step "3/4 End-to-end acceptance test through the Gateway"
bash scripts/e2e-test.sh "${1:-}"

step "4/4 k6 smoke test (1 VU, every public flow)"
net=$(docker inspect -f '{{range $k, $v := .NetworkSettings.Networks}}{{$k}}{{end}}' "$("${COMPOSE[@]}" ps -q api-gateway)")
docker run --rm --network "$net" -v "$(pwd -W 2>/dev/null || pwd)/k6:/scripts" \
  -e BASE_URL=http://api-gateway:8080 -e KEYCLOAK_URL=http://keycloak:8180 \
  -e CUSTOMER_PASSWORD="$(grep '^CUSTOMER_USER_PASSWORD=' deployment/docker/.env | cut -d= -f2-)" \
  grafana/k6:1.3.0 run --quiet /scripts/smoke-test.js

printf '\n\033[32mAll test levels passed.\033[0m\n'
