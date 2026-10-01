# Architecture Decision Document — Team 1

**Product:** Enterprise E-Commerce Platform · **Primary Bonus:** B1 — Product Reviews & Ratings (`review-service`, port 8086)
**Stack:** Java 21, Spring Boot 3.5.16, Spring Cloud 2025.0.3

Sections §5–§8 are first versions. We update them as L2–L5 produce real evidence (test results, k6 numbers, traces).

| Section | Author | Peer reviewer |
| --- | --- | --- |
| §1 Problem statement, §2 Bounded context | Ahmed Qamar | Sahar Attia |
| §3 API contract + events, §4 Data model | Sahar Attia | Ahmed Qamar |
| §5 Communication, §6 Failure modes | Ahmed Khalaf | Hussein Elsaka |
| §7 Security & deployment, §8 Test & load plan | Hussein Elsaka | Ahmed Khalaf |

---

## 1. PROBLEM STATEMENT

**Users:** shoppers who browse the catalogue (anonymous or signed in), customers who place orders, and admins who manage the catalogue and stock.

**Pain:** shoppers hesitate to buy because the catalogue shows no ratings or reviews from other buyers (no social proof). Admins have no signal about which products customers like.

**Success measures:**

- A signed-in customer can submit one review (rating 1–5 plus text) per product and read others' reviews page by page.
- Product detail shows `averageRating` and `reviewCount` within 5 seconds of a review being submitted (eventual consistency through Kafka).
- The average does not drift: delivering the same `ReviewSubmitted` event twice leaves the count unchanged (tested).
- Adding reviews keeps the platform targets: cached `GET /api/v1/products/{id}` P95 < 200 ms.

- **DECISION:** build B1 as a new `review-service` that owns reviews, and give Product a small rating read model updated by events.
- **OPTIONS CONSIDERED:** (a) new review-service + event projection into Product; (b) add review tables to product-service; (c) Product calls Review over Feign on every product detail read.
- **REASON:** (a) keeps review writes away from the catalogue database and keeps product reads fast because the rating is already stored next to the product. (b) mixes two owners of data in one service. (c) adds a synchronous call to the hottest read path and makes product detail fail when Review is down.
- **TRADE-OFF:** the rating on product detail is eventually consistent (a few seconds behind). We accept this because a rating does not need to be exact the moment it is written.
- **WHAT WOULD MAKE US REVISIT:** a requirement that the rating must be exact immediately after submit, or review volume so high that per-event updates in Product become a bottleneck (then we batch the projection).

## 2. BOUNDED CONTEXT

**review-service owns:** reviews (who reviewed which product, rating, text, time), the rule "one review per customer per product", and the `ReviewSubmitted` event.

**review-service does not own:** products, prices, categories (Product), orders or purchases (Order), customer identity (Keycloak). It stores `productId` and `customerId` only as references.

**product-service owns the rating projection:** `product_rating` (review count and rating sum per product) is Product's own read model, built from `ReviewSubmitted`. Product never reads review-service's database.

**Data ownership:** review-service has its own PostgreSQL database `review_db` with its own Flyway migrations. No other service connects to it.

**Existing services touched:** api-gateway (new route), product-service (new consumer + two fields in product detail), Keycloak (new receiving audience for review-service), Helm/CI (new chart and pipeline).

- **DECISION:** new service, separate database, references by ID only.
- **OPTIONS CONSIDERED:** new service vs. extending product-service; validating the product exists synchronously (Feign to Product) vs. not validating in Core.
- **REASON:** a separate service follows database-per-service and lets reviews be deployed and scaled on their own. We do not call Product on submit in Core: it would add a second sync dependency and a new service-client permission for little value, because Product's projection simply ignores events for products it does not know.
- **TRADE-OFF:** a review can be stored for a product ID that does not exist (or was deleted). It never appears on a real product page, but it does sit in `review_db`.
- **WHAT WOULD MAKE US REVISIT:** the verified-purchase stretch goal (needs Feign to Order with a circuit breaker), or spam reviews on fake product IDs becoming visible somewhere.

## 3. API CONTRACT + EVENTS

All public APIs are under `/api/v1`. Errors use one JSON shape: `{"status": 409, "error": "Conflict", "message": "...", "path": "..."}`.

### 3.1 Core platform endpoints

| Service | Method and path | Access (user role / service client) | Success | Errors |
| --- | --- | --- | --- | --- |
| product | `GET /api/v1/products?page&size` | Public | 200 page of products | 400 bad paging |
| product | `GET /api/v1/products/{id}` | Public | 200 product incl. `categoryName`, `averageRating`, `reviewCount` | 404 |
| product | `POST /api/v1/products` · `PUT /api/v1/products/{id}` · `DELETE /api/v1/products/{id}` | ADMIN via `gateway-service` | 201 / 200 / 204 | 400, 401, 403, 404 |
| order | `POST /api/v1/orders` | CUSTOMER via `gateway-service` | 201 `{orderId, status: PENDING}` | 400, 401, 409 out of stock, 503 inventory unavailable |
| order | `GET /api/v1/orders/{id}` | CUSTOMER (owner only) | 200 | 404 (also for another customer's order) |
| order | `GET /api/v1/orders` | CUSTOMER (own orders) | 200 page | 401 |
| inventory | `GET /api/v1/inventory/check?productId&quantity` | `order-service` client only, no public route | 200 `{available}` | 400, 403 |
| inventory | `GET /api/v1/inventory/{productId}` · `PUT /api/v1/inventory/{productId}` | ADMIN via `gateway-service` | 200 | 400, 403, 404 |
| payment | `POST /api/v1/payments` (header `Idempotency-Key`) | `payment-operator` client only, no public route | 201 (first) / 200 (repeat, same body) | 400 missing key, 403 |
| payment | `POST /api/v1/payments/{id}/refund` | `payment-operator` client only | 200 | 403, 404, 409 already refunded |
| all | `GET /actuator/health`, `/actuator/prometheus` | Internal network only, never routed by Gateway | 200 | — |

### 3.2 B1 review endpoints

| Method and path | Access | Request | Success | Errors |
| --- | --- | --- | --- | --- |
| `POST /api/v1/products/{productId}/reviews` | CUSTOMER via `gateway-service` | `{"rating": 1..5, "text": "≤ 2000 chars"}` | 201 `{reviewId, productId, rating, text, createdAt}` | 400 rating out of range, 401, 409 customer already reviewed this product |
| `GET /api/v1/products/{productId}/reviews?page&size` | Public | — | 200 page, newest first | 400 bad paging |

The customer ID comes from the Gateway-injected identity header, never from the request body. The Gateway route for `/api/v1/products/*/reviews/**` is declared **before** the general `/api/v1/products/**` route so it reaches review-service, not product-service.

### 3.3 Events

Every event carries `eventId` (UUID), `eventType`, `version` (starts at `1`), and `occurredAt`. The Kafka key is the aggregate ID (orderId or productId) so events for one aggregate stay in order on one partition.

| Event | Producer | Consumers | Topic | Business fields |
| --- | --- | --- | --- | --- |
| OrderPlaced | order | inventory | `order-events` | orderId, customerId, items[{productId, quantity}], totalAmount |
| InventoryReserved | inventory | payment | `inventory-events` | orderId, totalAmount |
| InventoryReservationFailed | inventory | order | `inventory-events` | orderId, reason |
| PaymentCompleted | payment | order | `payment-events` | orderId, paymentId, amount |
| PaymentFailed | payment | order, inventory | `payment-events` | orderId, reason |
| InventoryReleased | inventory | (audit only) | `inventory-events` | orderId |
| OrderConfirmed | order | notification | `order-events` | orderId, customerId |
| OrderCancelled | order | notification | `order-events` | orderId, customerId, reason |
| **ReviewSubmitted** (B1) | review | product | `review-events` | reviewId, productId, customerId, rating |

- **DECISION:** URL versioning `/api/v1`, a `version` field in every event, and a new `review-events` topic for B1.
- **OPTIONS CONSIDERED:** header versioning vs. URL versioning; putting `ReviewSubmitted` on an existing topic vs. a new topic.
- **REASON:** URL versions are visible in curl, Gateway routes, and logs. A separate topic keeps review traffic from mixing with the order Saga and lets Product subscribe to only what it needs. A topic is not new infrastructure.
- **TRADE-OFF:** a breaking change needs a `/api/v2` path and a new event version that consumers must handle side by side for a while.
- **WHAT WOULD MAKE US REVISIT:** a second consumer of reviews (for example a moderation service) that needs fields we do not publish — we would add them as an additive v1 change rather than a v2.

## 4. DATA MODEL

One PostgreSQL instance, one database and one owner role per service. Every schema change is a Flyway migration in `src/main/resources/db/migration`, named `V<n>__<description>.sql`. Migrations are only added, never edited after merge.

| Database | Table | Key columns | Notes |
| --- | --- | --- | --- |
| product_db | `category` | `id` PK, `name` UNIQUE | |
| product_db | `product` | `id` PK, `category_id` FK, `name`, `price`, `version` | optimistic locking on `version` |
| product_db | `product_rating` (B1) | `product_id` PK/FK, `review_count`, `rating_sum` | average = `rating_sum / review_count`, computed on read |
| product_db | `processed_event` | `event_id` PK, `processed_at` | dedup for `ReviewSubmitted` |
| order_db | `orders` | `id` PK, `customer_id` (ownership), `status`, `total_amount`, `created_at` | index on `customer_id` |
| order_db | `order_item` | `id` PK, `order_id` FK, `product_id`, `quantity`, `unit_price` | |
| order_db | `outbox_event` | `id` PK, `aggregate_id`, `event_type`, `payload` JSONB, `created_at`, `published_at` NULL | poller reads rows with `published_at IS NULL` |
| order_db | `processed_event` | `event_id` PK | |
| inventory_db | `stock` | `product_id` PK, `available`, `reserved`, `version` | `CHECK (available >= 0)` |
| inventory_db | `reservation` | `order_id` + `product_id` PK, `quantity`, `status`, `updated_at` | used for the NFR-05 30-second query |
| inventory_db | `outbox_event`, `processed_event` | as in order_db | |
| payment_db | `payment` | `id` PK, `order_id` UNIQUE, `idempotency_key` UNIQUE, `amount`, `status` | one outcome per order |
| payment_db | `outbox_event`, `processed_event` | as in order_db | |
| review_db (B1) | `review` | `id` PK, `product_id`, `customer_id`, `rating` `CHECK (1..5)`, `text`, `created_at`; **UNIQUE (`product_id`, `customer_id`)** | index on (`product_id`, `created_at DESC`) for paging |
| review_db (B1) | `outbox_event` | as in order_db | |

notification-service has no database.

**Migration plan for B1:** `review-service` `V1__create_review_and_outbox.sql`; `product-service` `V<next>__create_product_rating_and_processed_event.sql`. Both ship in L6.

- **DECISION:** store `rating_sum` and `review_count` in Product, not a pre-computed average; enforce "one review per customer per product" with a database unique constraint.
- **OPTIONS CONSIDERED:** store the average as a decimal; check for an existing review in code only.
- **REASON:** sum and count can be updated with one atomic `UPDATE ... SET review_count = review_count + 1, rating_sum = rating_sum + ?`, with no rounding drift. A unique constraint still holds when two submit requests race; a code-only check does not.
- **TRADE-OFF:** the average is computed on every read (cheap: one division, and the product is cached).
- **WHAT WOULD MAKE US REVISIT:** the review edit/delete stretch goal — then the event must carry the old and new rating so the sum can be corrected.

## 5. COMMUNICATION

| Interaction | Style | Reason |
| --- | --- | --- |
| Client → Gateway → services | Sync HTTP | the user waits for the answer |
| Order → Inventory stock check | Sync OpenFeign + Resilience4j (CircuitBreaker, Retry, Bulkhead, TimeLimiter) | Order must know "can I accept this now?" before answering |
| Order → Inventory → Payment → Order (Saga) | Async Kafka | the business transaction continues after the user already has `PENDING` |
| Order → Notification | Async Kafka | sending a message must never block or fail an order |
| Review → Product rating (B1) | Async Kafka (`ReviewSubmitted`) | Product reads must not depend on Review being up |

**Saga style — DECISION:** Choreography with a transactional outbox and idempotent consumers.

- **OPTIONS CONSIDERED:** Choreography vs. Orchestration (a central Saga coordinator with state in a table).
- **REASON:** three participants, a linear flow, and no branching rules. Choreography needs no coordinator to keep alive. "Where is order X?" is answered by the order status column plus a Zipkin trace.
- **TRADE-OFF:** the flow is spread over three services, so a new teammate must read three listeners to understand it. We reduce this with the event table in §3.3 and the sequence drawing in `docs/architecture/`.
- **WHAT WOULD MAKE US SWITCH:** a branching rule (fraud check, loyalty points, partial refunds) or a fourth participant. Then we move to Orchestration with Saga state persisted in order_db.

**Event publishing — DECISION:** transactional outbox with a polling publisher in Order, Inventory, Payment, and Review.

- **OPTIONS CONSIDERED:** outbox vs. publishing to Kafka directly after the database commit.
- **REASON:** with the outbox, the state change and the event are written in the same local transaction, so there is no window where the database says `PENDING` but `OrderPlaced` was never sent.
- **TRADE-OFF:** events go out with a delay of up to one poll interval (planned 500 ms), and an event can be published twice if the poller crashes after sending but before marking the row. Every consumer is idempotent (`processed_event` table) to cover this.
- **WHAT WOULD MAKE US REVISIT:** if the poll delay shows up as a real problem in the L5 k6 results, we look at change-data-capture — but that is new infrastructure and needs its own justification.

## 6. FAILURE MODES

| # | What fails | How we detect it | What the user sees | Mitigation |
| --- | --- | --- | --- | --- |
| F1 | **Duplicate `ReviewSubmitted` makes the average drift** (handbook B1 risk) | Test redelivers the same event; Grafana compares `review_count` with the review table count | Wrong average or count on the product page | `processed_event` insert and rating `UPDATE` in one transaction; duplicate `eventId` → skip. Test `ProductRatingProjectionIT.duplicateEventCountedOnce` |
| F2 | **review-service missing from Gateway routes or Helm** (handbook B1 risk) | Smoke test `k6/smoke-test.js` calls the review endpoints through the Gateway; ArgoCD shows the app missing | 404 on review endpoints, or reviews routed to product-service | Explicit route ordering test in Gateway; review chart added to the ArgoCD app list in the same PR as the service; CI fails if a service folder has no chart |
| F3 | Payment service is down | Kafka consumer lag for payment-service rises (Grafana panel, L5) | Order stays `PENDING` longer; never lost (NFR-01) | Events wait in Kafka; Payment resumes from its committed offset on restart. Bounded Retry + CircuitBreaker around the simulated charge; exhausted retries publish `PaymentFailed` → compensation |
| F4 | Inventory down during stock check | CircuitBreaker opens; `resilience4j_circuitbreaker_state` metric | `503` quickly instead of a long hang; no order created | TimeLimiter (2 s) + Retry (2 attempts) + CircuitBreaker; no outbox row written when the check fails |
| F5 | Notification send keeps failing | Messages appear on `order-events.DLT`; error log with trace ID | No email/notice for that order (order itself is correct) | `@RetryableTopic` with backoff, then DLT; failed messages can be replayed from the DLT |
| F6 | Same customer submits two reviews at the same moment | Unique constraint violation in logs | Second request gets `409` | UNIQUE (`product_id`, `customer_id`) in review_db |
| F7 | Outbox poller crashes after send, before marking the row | Same `eventId` seen twice by a consumer (dedup counter metric) | Nothing — consumers ignore the duplicate | Idempotent consumers in every service (§5) |

If the Architecture Review at S25 raises risks that differ from F1 and F2, we add them here with the same columns.

- **DECISION:** every consumer is idempotent by `eventId`, and every state change happens only from an expected state (Order moves only from `PENDING`).
- **OPTIONS CONSIDERED:** rely on Kafka exactly-once transactions; rely on consumers never receiving duplicates.
- **REASON:** Kafka exactly-once does not cover our database writes; at-least-once delivery plus idempotent consumers is the handbook default (NFR-10) and is testable.
- **TRADE-OFF:** one extra table and one extra insert per consumed event.
- **WHAT WOULD MAKE US REVISIT:** `processed_event` growing large enough to slow inserts — then we add a cleanup job that deletes rows older than 7 days.

## 7. SECURITY & DEPLOYMENT

**Identity:** Keycloak realm `ecommerce-platform`, roles `ADMIN` and `CUSTOMER`, test users `admin-test` and `customer-test`. Users sign in through the `user-sign-in` client.

**Token propagation:** the Gateway validates the user's JWT, removes any `X-User-*` headers sent by the client, adds `X-User-Id` and `X-User-Roles` from the validated token, and replaces the bearer token with its own `gateway-service` Client Credentials token. Business services check issuer, signature, lifetime, audience, and the calling client. The L0 realm (`deployment/docker/keycloak/realm-export.json`) defines the clients and roles but no audience mappers yet; S1 adds one audience mapper per receiving service, and every service rejects tokens without its own audience.

| Endpoint group | Allowed caller | User role |
| --- | --- | --- |
| Product reads, review reads | `gateway-service` (or anonymous through Gateway) | none |
| Product writes, inventory admin | `gateway-service` | ADMIN |
| Order endpoints, review submit | `gateway-service` | CUSTOMER (ownership checked in the service) |
| Inventory stock check | `order-service` | none |
| Payment create / refund | `payment-operator` | none |
| Actuator `/actuator/health`, `/actuator/prometheus` | not routed through the Gateway; reachable only inside the service network (Compose network, cluster) | none |
| Anything else | denied | — |

**Rate limits (FR-13, Redis):** Spring Cloud Gateway `RedisRateLimiter` on the public routes.

| Client | Key | Replenish rate | Burst capacity |
| --- | --- | --- | --- |
| Anonymous | client IP | 100 requests/s | 200 |
| Signed in | JWT subject | 20 requests/s | 40 |

`X-Forwarded-For` is trusted only from the ingress; otherwise the connection's remote address is the key. S5 puts both rates in `config-repo/api-gateway.yml`, so a test run can override them without a rebuild.

These numbers are checked against the §8 k6 scenarios, which all run from one machine (one IP) and use the single `customer-test` user:

- Product read load needs ≥ 50 req/s from one IP, which is below the 100 req/s anonymous rate.
- Order load uses 20 VUs with `sleep(1)` per iteration, so each VU sends at most one request per second: ≤ 20 req/s for one subject, within the 20 req/s rate and the 40 burst.
- The smoke test uses 1 VU with `sleep(1)`, about 1 req/s.
- The stress test deliberately goes past 100 req/s, so it runs with the anonymous rate raised through `config-repo/api-gateway.yml` (see §8). A separate check shows that the default limit returns 429 to the over-limit client and keeps serving a second client.

**Secrets:** never in Git. Compose reads them from `deployment/docker/.env`, which is in `.gitignore`; `deployment/docker/.env.example` lists every variable with `CHANGE_ME` values. The realm file holds `${GATEWAY_CLIENT_SECRET}`, `${ORDER_CLIENT_SECRET}`, `${PAYMENT_OPERATOR_CLIENT_SECRET}`, `${ADMIN_USER_PASSWORD}`, and `${CUSTOMER_USER_PASSWORD}` placeholders, which Keycloak replaces from the container environment at import. Kubernetes uses Secrets; CI uses GitHub Actions secrets.

**Images:** multi-stage Dockerfile per app (`maven:3.9.9-eclipse-temurin-21` build stage, `eclipse-temurin:21-jre` runtime), runs as the non-root user `app` (UID 10001), `HEALTHCHECK` with `curl` on `/actuator/health`.

**Kubernetes:** local **kind** cluster. One Helm chart per service under `deployment/helm/`. Readiness probe `/actuator/health/readiness`, liveness `/actuator/health/liveness`. One ServiceAccount per service with no extra permissions (`automountServiceAccountToken: false`). Only the Gateway is exposed; business services are `ClusterIP`.

**GitOps:** one ArgoCD Application per service with automated sync, `selfHeal: true`, `prune: true`.

**Replaced defaults:** none. We keep every handbook default (Eureka, Config Server, Redis, OpenFeign, Choreography, Outbox, Helm, ArgoCD). Version note: Spring Cloud 2025.0.3 is the line matching Boot 3.5.x and has reached the end of open-source support; we accept this for the capstone because the handbook fixes Boot 3.x.

- **DECISION:** Gateway is the only trust boundary for users; services trust identity headers only from the `gateway-service` client.
- **OPTIONS CONSIDERED:** forward the user's JWT to every service; use Client Credentials with injected identity headers.
- **REASON:** the service token lets each service check exactly which client may call which endpoint, and a stolen user token cannot call internal endpoints such as stock check or payment.
- **TRADE-OFF:** services see the user only through headers, so header stripping at the Gateway must be tested (S1).
- **WHAT WOULD MAKE US REVISIT:** a need for services to call each other on behalf of a user (token exchange), or adding Istio mTLS.

## 8. TEST & LOAD PLAN + RISKS

**Tests:**

- **Unit** (JUnit 5, Mockito only at system boundaries such as Feign and Kafka): service-layer rules — idempotent payment, order state transitions, rating sum/count update.
- **Web slice** (`@WebMvcTest`): role and ownership rules per controller.
- **Integration** (Testcontainers, real PostgreSQL and Flyway): at least one per database-owning service — Product, Order, Inventory, Payment, Review.
- **Kafka integration:** duplicate delivery and DLT behaviour (S10, S11, S12, S15).
- **Contract (optional):** Pact test for the Order ↔ Inventory stock check, recommended by the handbook. We add it after S6 and S8 only if L2 finishes early; the stock-check rules are already covered by the web-slice and integration tests.
- **Coverage target:** ≥ 60% line coverage on service layers. Today the parent POM runs JaCoCo `prepare-agent` and `report` on `mvn verify`, and CI runs `mvn -B verify`. The first service tests (L1) add a `jacoco:check` rule (`LINE` ≥ 0.60 on the `service` packages) so the CI build fails below the target.

**k6 scenarios (Gate G3):**

| Scenario | Target |
| --- | --- |
| Smoke: 1 VU, `sleep(1)`, every public endpoint (reviews from L6) | 0 errors |
| Load: `GET /api/v1/products` and `/{id}` through Gateway, anonymous | P95 < 200 ms (cached), ≥ 50 req/s, no 429 |
| Load: `POST /api/v1/orders`, 20 VUs, `customer-test` token, `sleep(1)` per iteration | P95 < 800 ms, no 429 |
| Stress: ramp product reads until errors > 1%, with the anonymous rate raised in `config-repo/api-gateway.yml` | record the breaking point and the bottleneck (with the default limit, the first errors would only be 429s) |

**Top 5 project risks:**

| Risk | Owner | Mitigation |
| --- | --- | --- |
| L3 Saga + outbox takes longer than the 5-day window | Sahar Attia | start the outbox in L2 (S8); daily check-in on S10/S11 progress |
| L4 kind + Helm + ArgoCD setup problems on members' machines | Hussein Elsaka | set up kind on one machine first, document the exact commands, then repeat on the others |
| Keycloak audience/client configuration mistakes block all services | Sahar Attia | test the token rules in S1 before any other service depends on them |
| Rating average drifts under duplicate events (B1) | Ahmed Qamar | idempotent projection + duplicate-event test (F1) |
| One member unavailable before a gate | Ahmed Khalaf | backups listed in the Team Charter; every area has a second person who can demo it |

- **DECISION:** real PostgreSQL in persistence tests (Testcontainers), not H2.
- **OPTIONS CONSIDERED:** H2 in-memory database; Testcontainers.
- **REASON:** Flyway migrations, unique constraints, and JSONB behave exactly as in production.
- **TRADE-OFF:** slower test runs and Docker required on every machine and in CI.
- **WHAT WOULD MAKE US REVISIT:** the CI test stage taking more than 10 minutes — then we reuse one container per test class group.
