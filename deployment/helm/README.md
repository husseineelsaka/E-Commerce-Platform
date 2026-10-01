# Helm

One chart per application (`config-server`, `eureka-server`, `api-gateway`, `product-service`, `order-service`, `payment-service`, `inventory-service`, `notification-service`), all with the same structure:

- `Deployment` — non-root (`runAsUser: 10001`, `runAsNonRoot`), no privilege escalation, read-only root filesystem with an `emptyDir` on `/tmp`, all capabilities dropped; startup, liveness (`/actuator/health/liveness`), and readiness (`/actuator/health/readiness`) probes on the management port; CPU/memory requests and a memory limit.
- `Service` — `ClusterIP` for every application except `api-gateway`, which is the only `NodePort` (30080). Business services are never exposed outside the cluster (ADD §7).
- `ServiceAccount` — one per application, no Role bound, `automountServiceAccountToken: false` (least privilege, NFR-04).
- Secrets — passwords and client secrets come from the `ecommerce-secrets` Secret (`secretEnv` in `values.yaml`), created from `deployment/docker/.env` at deploy time and never committed.
- Plain settings (`env` in `values.yaml`) point at the platform services by name.

`values.yaml` defaults to the CI images `ghcr.io/husseineelsaka/<app>`; `deployment/kubernetes/up.sh` overrides the image with the locally built one.

```sh
helm lint deployment/helm/*
helm template order-service deployment/helm/order-service
```
