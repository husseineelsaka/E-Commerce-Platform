# Run guide and service explainer

How to run the whole platform or one service, how to test it, and what every part does, why we need it, and how it
works. Use it to prepare the demo and the S29 oral Q&A. Commands run from the repository root in Git Bash (Windows) or
any Bash shell.

## 1. What runs where

| Part | Port | Published on the host? | Store | Starts after |
| --- | --- | --- | --- | --- |
| config-server | 8888 | No (Compose) / yes with `mvn` | `config-repo/` folder | nothing (always first) |
| eureka-server | 8761 | No (Compose) / yes with `mvn` | in memory | config-server |
| api-gateway | 8080 (API), 8090 (Actuator) | **8080 only** | Redis (rate limit) | eureka, keycloak, redis |
| product-service | 8081 | No | `product_db` + Redis cache | eureka, keycloak, redis |
| order-service | 8082 | No | `order_db` (+ outbox) | eureka, keycloak, kafka |
| payment-service | 8083 | No | `payment_db` (+ outbox) | eureka, keycloak, kafka |
| inventory-service | 8084 | No | `inventory_db` (+ outbox) | eureka, keycloak, kafka |
| notification-service | 8085 | No | none | eureka, kafka |
| review-service (B1) | 8086 | No | `review_db` (+ outbox) | eureka, keycloak, kafka |
| PostgreSQL | 5432 | yes | 5 databases, one owner role each | — |
| Kafka (+ Zookeeper 2181) | 9092 host / 29092 inside | yes | topics | zookeeper |
| Redis | 6379 | yes | cache + token buckets | — |
| Keycloak | 8180 | yes | realm `ecommerce-platform` | — |
| Zipkin / Prometheus / Grafana | 9411 / 9090 / 3000 | yes | traces / metrics / dashboards | — |
| Swagger UI | 8089 | yes | `docs/api/openapi.yaml` | — |
| storefront (web UI) | 8070 | yes | `frontend/site` (nginx, proxies /api and /auth) | api-gateway, keycloak |

Only the Gateway is published for API traffic (ADD §7). To reach a business service directly, call it from inside
the Compose network: `docker compose ... exec order-service curl http://product-service:8081/...`.

## 2. Prerequisites (once)

1. Java 21, Maven 3.9, Docker Desktop (4 CPUs and 8 GB for the Docker VM recommended), Git Bash, curl, python.
2. Secrets file (never committed):

   ```sh
   cp deployment/docker/.env.example deployment/docker/.env
   # edit deployment/docker/.env and replace every CHANGE_ME value
   ```

3. Optional for Kubernetes: kind, kubectl, helm. Optional for load tests: the `grafana/k6:1.3.0` image.

## 3. Run the whole platform (recommended)

```sh
docker compose -f deployment/docker/docker-compose.yml --env-file deployment/docker/.env up -d --build --wait
docker compose -f deployment/docker/docker-compose.yml --env-file deployment/docker/.env ps   # every row "healthy"
source scripts/demo-env.sh      # sets $GW, $COMPOSE and the tokens $CUSTOMER / $ADMIN (valid 5 minutes)
ready                           # "platform ready": the order path answers end to end
```

The first build takes several minutes (Maven inside each image). `--wait` returns when every container is healthy.
Then follow `docs/DEMO.md`, or run the automated checks in section 6.

Stop it: `$COMPOSE down` (keeps data) or `$COMPOSE down -v` (deletes databases, Kafka topics and the Keycloak realm,
which is re-imported on the next start).

## 4. Run one service at a time (development)

Start the infrastructure in Compose, then any application with Maven in its own terminal. Order matters:
**Config Server → Eureka → the rest**.

```sh
docker compose -f deployment/docker/docker-compose.yml --env-file deployment/docker/.env \
  up -d postgres zookeeper kafka redis keycloak zipkin prometheus grafana
mvn -pl platform/config-server spring-boot:run      # terminal 1, wait for "Started"
mvn -pl platform/eureka-server spring-boot:run      # terminal 2
bash scripts/verify-l0.sh                           # infra healthy + config + eureka (L0 gate)
```

Each service needs only the variables below (values from `deployment/docker/.env`); hosts default to `localhost`.

| Service | Command | Required environment | Needs running |
| --- | --- | --- | --- |
| api-gateway | `mvn -pl platform/api-gateway spring-boot:run` | `GATEWAY_CLIENT_SECRET` | Keycloak, Redis |
| product-service | `mvn -pl services/product-service spring-boot:run` | `PRODUCT_DB_PASSWORD` | PostgreSQL, Redis, Keycloak, Kafka (rating events) |
| inventory-service | `mvn -pl services/inventory-service spring-boot:run` | `INVENTORY_DB_PASSWORD` | PostgreSQL, Keycloak, Kafka |
| payment-service | `mvn -pl services/payment-service spring-boot:run` | `PAYMENT_DB_PASSWORD` (`PAYMENT_FAILURE_RATE` optional) | PostgreSQL, Keycloak, Kafka |
| order-service | `mvn -pl services/order-service spring-boot:run` | `ORDER_DB_PASSWORD`, `ORDER_CLIENT_SECRET` | PostgreSQL, Keycloak, Kafka, product + inventory for placing orders |
| notification-service | `mvn -pl services/notification-service spring-boot:run` | none (`NOTIFICATION_FAIL` optional) | Kafka |
| review-service | `mvn -pl services/review-service spring-boot:run` | `REVIEW_DB_PASSWORD` | PostgreSQL, Keycloak, Kafka |

Example (Git Bash):

```sh
export PRODUCT_DB_PASSWORD="$(grep '^PRODUCT_DB_PASSWORD=' deployment/docker/.env | cut -d= -f2-)"
mvn -pl services/product-service spring-boot:run
curl http://localhost:8081/actuator/health                         # {"status":"UP"}
```

Each service is independent: it starts and answers its health check while the other business services are stopped.
Placing an order is the one flow that needs Product and Inventory up (it calls them synchronously).

Restart one service inside Compose (for example after a code change):

```sh
$COMPOSE up -d --build --wait order-service
$COMPOSE logs -f order-service
```

## 5. Run on Kubernetes (kind + Helm + ArgoCD)

```sh
docker compose ... up -d --build --wait          # images + infrastructure (Postgres, Kafka, Redis, Keycloak stay in Compose)
bash deployment/kubernetes/up.sh                 # kind cluster "ecommerce", loads images, helm install x9
kubectl -n ecommerce get pods                    # 9 pods Running and READY 1/1
curl http://localhost:30080/api/v1/products/1    # Gateway NodePort
```

GitOps: follow `deployment/argocd/README.md` (install ArgoCD, `kubectl apply -f deployment/argocd/applications.yaml`,
then `kubectl -n argocd get applications` shows 9 × `Synced` / `Healthy`). Remove: `kind delete cluster --name ecommerce`.
Do not run kind and the full Compose app set at the same time on a laptop: stop one (`docker stop ecommerce-control-plane`).

## 6. Test the project

| Level | What it proves | Command | Needs |
| --- | --- | --- | --- |
| Unit + web slice | business rules, role/ownership/audience rules per controller | `mvn -B test` | Java only |
| Integration (Testcontainers) | real PostgreSQL + Flyway, Kafka, Redis: outbox, duplicates, DLT, idempotency, races | `mvn -B verify` (also enforces ≥ 60 % service-layer coverage) | Docker |
| One service only | the same, for one module | `mvn -pl services/order-service -am verify` | Docker |
| **End-to-end** | every FR and the key NFRs through the real Gateway, Keycloak, Kafka | `bash scripts/e2e-test.sh` | stack running (section 3) |
| End-to-end + failures | compensation, Payment outage, circuit breaker, DLT (restarts containers) | `bash scripts/e2e-test.sh --chaos` | stack running |
| API collection | every endpoint, 60+ assertions | Postman: `docs/api/postman/` | stack running |
| Load | NFR-02 / NFR-03 numbers | `k6/README.md` (smoke, load, stress) | stack running, k6 image |
| **Everything in order** | levels 1 → e2e → k6 smoke, stops at the first failure | `bash scripts/test-all.sh [--chaos]` | all of the above |

`scripts/e2e-test.sh` prints one `PASS` / `FAIL` / `WARN` line per check with the requirement it proves, for example
`PASS FR-08 same key again -> 200 with the SAME paymentId (charged once)`. Its sections:

| Section | Checks |
| --- | --- |
| 0 Platform | every container healthy; Config Server serves `config-repo`; all 7 clients registered in Eureka |
| 1 Catalogue / security / cache | public paginated browse, `categoryName`, 401 without/with a bad token, 403 for CUSTOMER writes, forged `X-User-Roles` ignored, ADMIN create/update, Redis key created then evicted on write, 429 for a flooding client while another is served |
| 2 Inventory / payment / order | admin stock view/adjust/low-stock, CUSTOMER 403, 400/409 order rejections, ADMIN cannot order, order `PENDING` + frozen price, own orders only, service-to-service token rules, payment idempotency (201 → 200 same id → 422), one payment row, refund 200 then 409 |
| 3 Saga / tracing | `CONFIRMED`, stock reserved, outbox published, one notification, duplicate `PaymentCompleted` ignored, one Zipkin trace across Gateway → Order → Inventory → Payment, JSON log with trace id |
| 4 Reviews (B1) | 401 / 400 / 201 / 409, public list, rating projection (count 1, average 5) |
| 5 Observability | all Prometheus targets up, Grafana up |
| 6 Chaos (`--chaos`) | payment declined → `CANCELLED`, stock restored, no `RESERVED` row left (NFR-05), cancel notice; Payment stopped → `PENDING` then `CONFIRMED` after restart (NFR-01); Inventory stopped → 503 and fast-fail once the circuit is open; failing notifications end on `order-events.DLT` |

## 7. The services: what, why, how

### config-server — central configuration
- **What:** serves `config-repo/*.yml` to every application at startup.
- **Why we need it:** one place for shared settings (Eureka URL, tracing, Kafka observation, Actuator) instead of nine
  copies; environment-specific values without rebuilding images (12-Factor III).
- **How it works:** Spring Cloud Config Server, `native` profile reading a folder. Clients import
  `optional:configserver:http://config-server:8888` and get `application.yml` merged with `<their-name>.yml`.
  Secrets stay out: files contain `${ENV}` placeholders.

### eureka-server — service discovery
- **What:** registry of running instances by name.
- **Why:** containers and pods change address; callers use names (`lb://order-service`, `@FeignClient(name=...)`).
- **How:** every app registers `spring.application.name` and heartbeats every 30 s; the Gateway and Feign ask the
  registry and Spring Cloud LoadBalancer picks an instance (client-side load balancing).

### api-gateway — single entry point and trust boundary
- **What:** the only public door (port 8080): routing, JWT validation, role rules, identity hand-off, rate limiting.
- **Why:** security and limits enforced once; business services never exposed; clients see one URL.
- **How:** Redis token bucket per client (subject or IP) → validate Keycloak JWT (signature via JWKS, issuer, expiry,
  `azp=user-sign-in`) → route rule by method + path and role → strip client `X-User-*`, add `X-User-Id` /
  `X-User-Roles`, swap the bearer for its own `gateway-service` Client Credentials token → forward with `lb://`.
- **Details:** `platform/api-gateway/README.md`.

### product-service — catalogue
- **What:** public paginated browse and detail, admin create/update/delete, category name, rating projection (B1).
- **Why:** owns the catalogue and prices; the hottest read path, so it is cached.
- **How:** controller → `ProductService` → Spring Data JPA on `product_db` (Flyway seeds 4 categories and 20 products).
  Reads return a JPQL projection `ProductView` (with `categoryName`, `averageRating`, `reviewCount`), never the entity
  (CQRS read model, S18). Cache-aside in Redis: `products:item::<id>` and `products:list::<page>:<size>`; every write
  evicts the item and all list pages **after commit**. Security: reads only from `gateway-service` (or `order-service`
  for the price lookup); writes only from `gateway-service` with `X-User-Roles` containing `ADMIN`. Consumes
  `review-events` and updates `product_rating` (sum + count) idempotently by `eventId`.

### inventory-service — stock
- **What:** stock check (internal), reservation and release (Saga), admin view/adjust, low-stock list.
- **Why:** stock changes on every order and must never go negative; it is a different owner and change rate from
  the catalogue (Architecture Clinic #1).
- **How:** `stock(product_id, available, reserved, version)` with `CHECK (available >= 0)`. `GET /check` answers
  Order's synchronous question "is there enough?". On `OrderPlaced` it locks the order's rows in product-id order
  (no deadlocks), reserves all lines or none, and writes `InventoryReserved` or `InventoryReservationFailed` to its
  outbox in the same transaction. On `PaymentFailed` it releases the reservation and publishes `InventoryReleased`.

### order-service — place order and Saga outcome
- **What:** `POST /orders` returns at once with `orderId` + `PENDING`; read one order / list own orders; final status.
- **Why:** owns the order and its lifecycle; it is where the customer's request becomes a business transaction.
- **How (sync part):** for each item, Feign → Product for the price and Feign → Inventory for stock, both with an
  `order-service` Client Credentials token and four Resilience4j patterns. With Resilience4j's default aspect order
  they nest as **Retry (2 attempts) ( CircuitBreaker ( TimeLimiter 500 ms ( Bulkhead 20 ( call ) ) ) )**: each retry
  passes through the circuit breaker, so a retried failure counts twice. Out of stock → 409, dependency down → 503, nothing saved. Then **one local transaction** saves the
  order (`PENDING`), its items with the frozen `unit_price`, and an `OrderPlaced` outbox row.
- **How (async part):** `OutboxRelay` polls every 500 ms (`FOR UPDATE SKIP LOCKED`), publishes to `order-events`
  keyed by orderId, inside the original trace (`trace_parent` column). It consumes `payment-events` /
  `inventory-events`: `PaymentCompleted` → `CONFIRMED` + `OrderConfirmed`; `PaymentFailed` or
  `InventoryReservationFailed` → `CANCELLED` + `OrderCancelled`. Updates run `WHERE status='PENDING'` and record the
  `eventId` in `processed_event`, so duplicates and late events change nothing.

### payment-service — pay exactly once
- **What:** charges an order once (simulated), refunds, test switch `PAYMENT_FAILURE_RATE`.
- **Why:** retries and redelivery are normal in distributed systems; without idempotency a retry is a double charge.
- **How:** `payment` has `order_id UNIQUE` and `idempotency_key UNIQUE` (the Saga uses orderId as the key; the internal
  HTTP API uses the `Idempotency-Key` header) plus a SHA-256 `request_hash`. Same key + same body → stored result
  (200); same key + different body → 422. The charge runs inside Resilience4j Retry (3) + CircuitBreaker; when
  exhausted the payment is `FAILED` and `PaymentFailed` starts compensation. If the service is down, events wait in
  Kafka and orders stay `PENDING` (NFR-01). HTTP API callable only by the `payment-operator` client.

### notification-service — tell the customer
- **What:** confirmation and cancellation notices (log lines standing in for e-mail/SMS).
- **Why:** sending a message must never block or fail an order, so it is asynchronous and isolated.
- **How:** consumes only `OrderConfirmed` / `OrderCancelled` from `order-events`. A failing send is retried with
  `@RetryableTopic` (4 attempts, 1 s doubling, on `order-events-retry`), then parked on `order-events.DLT` with an
  `ALERT` log; DLT messages can be replayed. No database, so no deduplication (accepted in ADD §6).

### review-service — Bonus B1 reviews and ratings
- **What:** one review per customer per product (rating 1–5 + text), public paginated reads.
- **Why:** social proof for shoppers, kept out of the catalogue database so product reads stay fast.
- **How:** `UNIQUE (product_id, customer_id)` gives 409 even under a race; the review and a `ReviewSubmitted` outbox row
  are written in one transaction; the event goes to `review-events`; Product adds it to `rating_sum` / `review_count`
  once per `eventId`, and computes the average on read, so it never drifts.

### Infrastructure: why each piece is there

| Component | Why we need it | Where it is used |
| --- | --- | --- |
| PostgreSQL | durable data, **database per service** (each service owns its schema, Flyway migrations) | product, order, payment, inventory, review |
| Kafka | durable, replayable events for the Saga and projections; consumers can be down and catch up | `order-events`, `inventory-events`, `payment-events`, `review-events`, `*.DLT` |
| Redis | fast shared cache (product reads) and shared token buckets (rate limit across Gateway instances) | product-service, api-gateway |
| Keycloak | real identity provider: users, roles, OAuth2/OIDC tokens signed with RSA, service clients | all |
| Zipkin | distributed tracing: one trace id across HTTP and Kafka hops | all apps |
| Prometheus + Grafana | metrics scraped from `/actuator/prometheus`; dashboard "E-Commerce Platform" (RPS, P95, circuit breakers, consumer lag) | all apps |

## 8. One order, end to end

1. Client signs in to Keycloak (`user-sign-in`) and sends `POST /api/v1/orders` with the token to the Gateway.
2. Gateway: rate limit → validate JWT → role `CUSTOMER` → strip/add identity headers → swap token → route to Order.
3. Order: Feign price lookup (Product) and stock check (Inventory) with resilience; saves `PENDING` + `OrderPlaced`
   outbox row in one transaction; answers **201 `{orderId, PENDING}`** — the customer is done waiting.
4. Order's outbox poller publishes `OrderPlaced` → Inventory reserves → `InventoryReserved`.
5. Payment charges once → `PaymentCompleted` (or `PaymentFailed` after retries).
6. Order → `CONFIRMED` + `OrderConfirmed` (or Inventory releases and Order → `CANCELLED` + `OrderCancelled`).
7. Notification sends the notice. The client polls `GET /api/v1/orders/{id}`. Zipkin shows all of it as one trace.

## 9. Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| `401` on every call | token expired (5 min): run `tokens` again |
| `503 Product/Inventory unavailable` right after start | services still registering in Eureka (~30 s): run `ready` |
| Order stays `PENDING` | payment-service or inventory-service stopped, or Kafka down: `$COMPOSE ps`, start it; the order completes |
| Realm change has no effect | Keycloak imports only on first start: remove the `keycloak` container and `docker_keycloak_data` volume (README) |
| Port 5432 already used | set `POSTGRES_HOST_PORT=5433` in `.env` and the matching `*_DB_PORT` for services run with Maven |
| `Could not find a valid Docker environment` in tests | start Docker Desktop (Testcontainers needs it) |
| Laptop very slow | kind and Compose together: `docker stop ecommerce-control-plane` |
