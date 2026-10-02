# Kubernetes (kind)

Local cluster for L4: `kind-cluster.yaml` (cluster `ecommerce`, host port 30080 → Gateway NodePort) and `up.sh`, which deploys the nine Helm charts from `deployment/helm`.

Infrastructure stays in Docker Compose. `up.sh` connects the kind node to the Compose network, so pods reach `postgres`, `kafka:29092`, `redis`, and `keycloak:8180` by name.

```sh
docker compose -f deployment/docker/docker-compose.yml --env-file deployment/docker/.env up -d --build --wait
bash deployment/kubernetes/up.sh
kubectl -n ecommerce get pods
curl http://localhost:30080/api/v1/products/1
```

`up.sh` creates the `ecommerce-secrets` Secret from `deployment/docker/.env` (`kubectl create secret --from-env-file`); the file is gitignored and the Secret exists only in the cluster.

Remove the cluster with `kind delete cluster --name ecommerce`.
