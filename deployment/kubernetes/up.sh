#!/usr/bin/env bash
# Creates the kind cluster and installs the nine applications with Helm.
# Infrastructure (PostgreSQL, Kafka, Redis, Keycloak) keeps running in Docker Compose; the kind node joins the Compose
# network so pods reach it by service name (postgres, kafka:29092, redis, keycloak:8180).
set -euo pipefail
cd "$(dirname "$0")/../.."
ENV_FILE=deployment/docker/.env
APPS=(config-server eureka-server api-gateway product-service inventory-service payment-service order-service notification-service review-service)
NETWORK=${COMPOSE_NETWORK:-docker_default}
TAG=${IMAGE_TAG:-local}

kind get clusters | grep -qx ecommerce || kind create cluster --config deployment/kubernetes/kind-cluster.yaml
docker network connect "$NETWORK" ecommerce-control-plane 2>/dev/null || true

for app in "${APPS[@]}"; do
  docker image inspect "ecommerce/$app:$TAG" >/dev/null 2>&1 || {
    echo "Missing image ecommerce/$app:$TAG - build it with: docker compose -f deployment/docker/docker-compose.yml build" >&2; exit 1; }
  kind load docker-image --name ecommerce "ecommerce/$app:$TAG"
done

kubectl create namespace ecommerce --dry-run=client -o yaml | kubectl apply -f -
kubectl -n ecommerce create secret generic ecommerce-secrets --from-env-file="$ENV_FILE" --dry-run=client -o yaml | kubectl apply -f -

for app in "${APPS[@]}"; do
  helm upgrade --install "$app" "deployment/helm/$app" --namespace ecommerce \
    --set image.repository="ecommerce/$app" --set image.tag="$TAG" --set image.pullPolicy=Never
  if [ "$app" = config-server ] || [ "$app" = eureka-server ]; then
    kubectl -n ecommerce rollout status "deployment/$app" --timeout=300s
  fi
done
for app in "${APPS[@]}"; do kubectl -n ecommerce rollout status "deployment/$app" --timeout=420s; done
echo "Gateway: http://localhost:30080"
