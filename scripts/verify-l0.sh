#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
compose=(docker compose -f deployment/docker/docker-compose.yml --env-file deployment/docker/.env)
failures=0
check() {
  local name="$1"
  shift
  if "$@"; then
    echo "PASS $name"
  else
    echo "FAIL $name"
    failures=$((failures + 1))
  fi
}
container_healthy() {
  local id state
  id="$("${compose[@]}" ps -q "$1")"
  [[ -n "$id" ]] || return 1
  state="$(docker inspect --format '{{.State.Health.Status}}' "$id")"
  [[ "$state" == healthy ]]
}
json_contains() {
  curl --fail --silent --show-error --max-time 5 -H 'Accept: application/json' "$1" | grep -q "$2"
}
for service in postgres zookeeper kafka redis keycloak zipkin prometheus grafana; do
  check "compose $service healthy" container_healthy "$service"
done
check "config-server health UP" json_contains http://localhost:8888/actuator/health '"status":"UP"'
check "config-server serves application/default" json_contains http://localhost:8888/application/default '"name":"application"'
check "eureka health UP" json_contains http://localhost:8761/actuator/health '"status":"UP"'
check "CONFIG-SERVER registered" json_contains http://localhost:8761/eureka/apps '"CONFIG-SERVER"'
if (( failures > 0 )); then
  echo "L0 verification: $failures failure(s)"
  exit 1
fi
echo "L0 verification: 12 checks passed"
