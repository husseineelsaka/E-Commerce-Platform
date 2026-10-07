# Enterprise E-Commerce Platform

Team 1's Java 21 / Spring Boot 3.5.16 / Spring Cloud 2025.0.3 monorepo: nine independent applications (Config Server, Eureka, Gateway, and the product, order, payment, inventory, notification, and review services) on shared local infrastructure.

| Layer | What it delivers | Phase document |
| --- | --- | --- |
| L0 | Monorepo, infrastructure, Config Server, Eureka, charter, ADD | [L0](docs/phases/L0.md) |
| L1 | Gateway security, product catalogue, Redis cache, rate limiting | [L1](docs/phases/L1.md) |
| L2 | Inventory, payment, place order with Feign and resilience | [L2](docs/phases/L2.md) |
| L3 | Choreography Saga with transactional outbox, idempotent consumers, DLT, notifications | [L3](docs/phases/L3.md) |
| L4 | Docker Compose, CI to GHCR, Helm, kind, ArgoCD | [L4](docs/phases/L4.md) |
| L5 | Tracing across HTTP and Kafka, Grafana, k6, [Performance Report](docs/PERFORMANCE-REPORT.md) | [L5](docs/phases/L5.md) |
| L6 | B1 Product Reviews & Ratings (`review-service`) | [L6](docs/phases/L6.md) |

## Team

| Member | GitHub |
| --- | --- |
| Hussein Elsaka | [@husseineelsaka](https://github.com/husseineelsaka) |
| Ahmed Khalaf | [@5alafawyyy](https://github.com/5alafawyyy) |
| Ahmed Qamar | [@AhmeddKamar](https://github.com/AhmeddKamar) |
| Sahar Attia | [@SaherAttia26](https://github.com/SaherAttia26) |

Working agreement: [Team Charter](docs/TEAM-CHARTER.md). Agent and contributor rules: [AGENTS.md](AGENTS.md).

## Documentation

| Document | Contents |
| --- | --- |
| [ADD](docs/adr/ADD-team-1.md) | The eight-section Architecture Decision Document: API contracts, events, data model, Saga, failure modes, security, test plan |
| [Architecture drawings](docs/architecture/README.md) | Service boundaries, order-flow sequence, data locations |
| [Backlog](docs/BACKLOG.md) | 15 stories with owners and Definitions of Done |
| [Phase documents](docs/phases/) | Decisions, tests, evidence, and gate status for L0–L6 |
| [Performance Report](docs/PERFORMANCE-REPORT.md) | k6 results and the Gateway bottleneck fix |
| [Demo script](docs/DEMO.md) | The 15-minute final demo, command by command |
| [API docs](docs/api/README.md) | OpenAPI spec, Swagger UI, Postman collection |
| Service READMEs | [product](services/product-service/README.md) · [order](services/order-service/README.md) · [payment](services/payment-service/README.md) · [inventory](services/inventory-service/README.md) · [notification](services/notification-service/README.md) · [review](services/review-service/README.md) |

## Requirements checklist

| Requirement | Where it is delivered |
| --- | --- |
| FR-01–FR-04, FR-15 — public catalogue, admin CRUD, category-name read model, Keycloak, Redis cache with eviction | L1: Gateway, product-service |
| FR-05, FR-06, FR-10 — place order (`PENDING`), Feign stock pre-check with Resilience4j, read/list own orders | L2: order-service, inventory-service |
| FR-07–FR-09 — reservation, idempotent payment, `CONFIRMED` / `CANCELLED` with compensation | L3: choreography Saga with outbox |
| FR-11 — notifications with retries and a dead-letter topic | L3: notification-service |
| FR-12 — admin stock view and adjust | L2: inventory-service |
| FR-13 — per-client rate limiting | L1: Gateway + Redis |
| FR-14 — Client Credentials between services | L1–L2: Gateway, order-service (ADD §7) |
| FR-16 — Bonus B1 Reviews & Ratings (Core) | L6: review-service |
| NFR-01, NFR-05, NFR-10 — Payment outage, no orphaned reservations, idempotent consumers + DLT | L3 |
| NFR-02, NFR-03 — latency and throughput | L5: [Performance Report](docs/PERFORMANCE-REPORT.md) |
| NFR-04, NFR-08 — security, Compose and Helm deployment | L4 |
| NFR-06 — one trace across HTTP and Kafka, JSON logs | L5 |
| NFR-07 — ≥ 60 % service-layer line coverage, Testcontainers per database | JaCoCo `check` in the parent `pom.xml` fails `mvn verify` below 60 % |
| NFR-09 — Flyway, `/api/v1` | every database-owning service |

## Prerequisites

Java 21 or newer, Maven, Docker Compose, Bash, curl, and jq (for the token example below). Run all Maven commands from the repository root. Config Server's default native path supports `mvn -pl platform/config-server spring-boot:run`; set `CONFIG_REPO_LOCATION` to a file URI for a different working directory.

## Start L0

```sh
cp deployment/docker/.env.example deployment/docker/.env
# Edit deployment/docker/.env and replace every CHANGE_ME value.
docker compose -f deployment/docker/docker-compose.yml --env-file deployment/docker/.env up -d
mvn -pl platform/config-server spring-boot:run
```

In another terminal after Config Server is healthy:

```sh
mvn -pl platform/eureka-server spring-boot:run
```

In a third terminal:

```sh
bash scripts/verify-l0.sh
```

Build all modules with `mvn -B verify`. Each module can run with `mvn -pl <module-path> spring-boot:run`. App Dockerfiles use the repository root as build context, for example `docker build -f platform/config-server/Dockerfile .`.

See [L0 phase](docs/phases/L0.md) for decisions and gate status.

## Gateway and Keycloak

Start Config Server, then Eureka as above. Set `KEYCLOAK_ISSUER_URI` to the externally reachable realm URL (default `http://localhost:8180/realms/ecommerce-platform`) and `GATEWAY_CLIENT_SECRET` to the `gateway-service` secret in `deployment/docker/.env`. Start the Gateway from the repository root:

```sh
export KEYCLOAK_ISSUER_URI=http://localhost:8180/realms/ecommerce-platform
export GATEWAY_CLIENT_SECRET='your-local-gateway-client-secret'
mvn -pl platform/api-gateway spring-boot:run
```

The Gateway validates `user-sign-in` tokens and obtains its own client credentials token for downstream requests. To get a customer token and verify that a protected admin route rejects it with `403`, use the local test user's password from `deployment/docker/.env` in another shell:

```sh
export CUSTOMER_USER_PASSWORD='your-local-customer-test-password'
TOKEN="$(curl --fail-with-body -sS -X POST 'http://localhost:8180/realms/ecommerce-platform/protocol/openid-connect/token' \
  --data-urlencode 'grant_type=password' \
  --data-urlencode 'client_id=user-sign-in' \
  --data-urlencode 'username=customer-test' \
  --data-urlencode "password=$CUSTOMER_USER_PASSWORD" | jq -er '.access_token')"
curl -i -X POST -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/products
```

Every Gateway route uses a Redis token bucket. Anonymous clients are keyed by IP (100 requests/s, burst 200); signed-in clients are keyed by JWT subject (20 requests/s, burst 40). A depleted bucket returns `429` for that client. Set `REDIS_HOST` (default `localhost`) and `REDIS_PORT` (default `6379`) for the Gateway. The rates are `gateway.rate-limit.anonymous.*` and `gateway.rate-limit.signed-in.*` in `config-repo/api-gateway.yml`, with the same fallback values in `platform/api-gateway/src/main/resources/application.yml`. The Gateway fails open if Redis is unavailable, so rate limits are temporarily unenforced during an outage.

Set `gateway.rate-limit.trusted-proxies` to a list of direct ingress IP addresses or CIDRs when an ingress sends `X-Forwarded-For`; it is empty by default. Only a direct peer on that list can supply a forwarded client IP. The resolver uses the rightmost forwarded address (one trusted ingress hop). Configure ingress to append the peer address to `X-Forwarded-For`.

After changing `deployment/docker/keycloak/realm-export.json`, recreate the local Keycloak container and its data volume. Keycloak imports the realm only on first start:

```sh
docker compose -f deployment/docker/docker-compose.yml --env-file deployment/docker/.env rm -sf keycloak
docker volume rm docker_keycloak_data
docker compose -f deployment/docker/docker-compose.yml --env-file deployment/docker/.env up -d keycloak
```

## Run the whole platform with Docker Compose

`deployment/docker/docker-compose.yml` runs the infrastructure **and** the nine applications (NFR-08). Images are built from each module's multi-stage Dockerfile (non-root user `app`, healthcheck).

```sh
cp deployment/docker/.env.example deployment/docker/.env   # then replace every CHANGE_ME value
docker compose -f deployment/docker/docker-compose.yml --env-file deployment/docker/.env up -d --build --wait
```

- Only the Gateway is published for API traffic: `http://localhost:8080`. Product, order, payment, inventory, notification, review, Config Server, and Eureka have no host port (ADD §7). Infrastructure keeps its host ports for development tools.
- Containers reach each other by service name (`postgres`, `kafka:29092`, `redis`, `keycloak:8180`, `config-server`, `eureka-server`).
- Keycloak runs with `KC_HOSTNAME=http://localhost:8180`, so every token has the issuer `http://localhost:8180/realms/ecommerce-platform` whichever address fetched it. Services validate that issuer and load signing keys from `KEYCLOAK_JWK_SET_URI` (`http://keycloak:8180/...` in Compose); the Gateway and order-service fetch tokens from `KEYCLOAK_TOKEN_URI`.
- Demo switches in `.env`: `PAYMENT_FAILURE_RATE=1.0` (every charge declines → compensation), `NOTIFICATION_FAIL=true` (every notification fails → `order-events.DLT`).
- `scripts/verify-l0.sh` checks Config Server and Eureka on the host ports used when they run with `mvn spring-boot:run`; in the full Compose mode use `docker compose ps`, which shows every container's health.

## Place an order and manage stock

With the whole platform running and `TOKEN` from the example above (a `customer-test` token), place an order and poll it. The API answers `201` with `PENDING`; the Saga moves it to `CONFIRMED` (or `CANCELLED` with stock released when `PAYMENT_FAILURE_RATE=1.0`):

```sh
ORDER_ID="$(curl -sS -X POST http://localhost:8080/api/v1/orders -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"items": [{"productId": 6, "quantity": 1}]}' | jq -er '.orderId')"
curl -s "http://localhost:8080/api/v1/orders/$ORDER_ID" -H "Authorization: Bearer $TOKEN" | jq '.status'
curl -s 'http://localhost:8080/api/v1/orders?page=0&size=20' -H "Authorization: Bearer $TOKEN" | jq   # own orders only
```

With an `admin-test` token in `ADMIN` (same request as above, `username=admin-test` and `ADMIN_USER_PASSWORD`), view and adjust stock:

```sh
curl -s http://localhost:8080/api/v1/inventory/6 -H "Authorization: Bearer $ADMIN" | jq
curl -s -X PUT http://localhost:8080/api/v1/inventory/6 -H "Authorization: Bearer $ADMIN" \
  -H 'Content-Type: application/json' -d '{"available": 500}' | jq
curl -s 'http://localhost:8080/api/v1/inventory/low-stock?threshold=10' -H "Authorization: Bearer $ADMIN" | jq   # lowest first
```

After `source scripts/demo-env.sh`, running `tokens` sets both tokens (`$CUSTOMER`, `$ADMIN`, valid for 5 minutes); [docs/DEMO.md](docs/DEMO.md) walks through every flow, including compensation and the Inventory-down circuit breaker.

## Reviews and ratings (B1)

A signed-in customer reviews a product once (rating 1–5 plus text); anyone reads reviews; product detail shows `averageRating` and `reviewCount`, updated through the `ReviewSubmitted` event within a few seconds. With `TOKEN` from the example above:

```sh
curl -i -X POST http://localhost:8080/api/v1/products/6/reviews -H "Authorization: Bearer $TOKEN"   -H 'Content-Type: application/json' -d '{"rating": 4, "text": "Does the job, quiet and fast."}'   # 201; again: 409
curl -s 'http://localhost:8080/api/v1/products/6/reviews?page=0&size=10' | jq        # public, newest first
curl -s http://localhost:8080/api/v1/products/6 | jq '{averageRating, reviewCount}'
```

## Kubernetes, observability, and load tests

- kind and Helm: [deployment/kubernetes/README.md](deployment/kubernetes/README.md); ArgoCD: [deployment/argocd/README.md](deployment/argocd/README.md).
- Zipkin `http://localhost:9411`, Prometheus `http://localhost:9090`, Grafana `http://localhost:3000` (dashboard "E-Commerce Platform").
- API docs: Swagger UI `http://localhost:8089` and a Postman collection for every request, in [docs/api](docs/api/README.md).
- k6 scripts and how to run them: [k6/README.md](k6/README.md); results: [docs/PERFORMANCE-REPORT.md](docs/PERFORMANCE-REPORT.md).
