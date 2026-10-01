# Backlog — Team 1

15 stories. Each is at most 4 hours. Every layer L1–L6 has at least one story, and every story names the test or command that proves it. Stories larger than 4 hours that appear later (for example the rest of L4 Helm charts) are split into new stories when that layer starts.

| # | Layer | Story | Owner | Estimate |
| --- | --- | --- | --- | --- |
| S1 | L1 | Gateway token validation and trusted identity | Sahar Attia | 4h |
| S2 | L1 | Public product browse with category name | Ahmed Khalaf | 4h |
| S3 | L1 | Admin product create / update / delete | Ahmed Khalaf | 3h |
| S4 | L1 | Product read cache with eviction | Hussein Elsaka | 3h |
| S5 | L1 | Public endpoint rate limiting | Ahmed Khalaf | 3h |
| S6 | L2 | Internal stock check and admin stock view/adjust | Ahmed Qamar | 4h |
| S7 | L2 | Idempotent payment and refund | Ahmed Qamar | 4h |
| S8 | L2 | Place order and read own orders | Sahar Attia | 4h |
| S9 | L3 | Payment from the Saga with retry and circuit breaker | Ahmed Qamar | 4h |
| S10 | L3 | Stock reservation and release from events | Ahmed Qamar | 4h |
| S11 | L3 | Saga outcomes and outbox publisher in Order | Sahar Attia | 4h |
| S12 | L3 | Notifications with retry and Dead Letter Topic | Ahmed Khalaf | 3h |
| S13 | L4 | CI image build and first Helm deploy to kind | Hussein Elsaka | 4h |
| S14 | L5 | Cross-hop trace and k6 product read test | Hussein Elsaka | 4h |
| S15 | L6 | Submit and read product reviews (B1 Core) | Ahmed Qamar | 4h |

**Per member:** Ahmed Khalaf 4 stories · Ahmed Qamar 5 · Sahar Attia 3 · Hussein Elsaka 3 (plus L0 platform work already in place and the remaining L4/L5 stories split out later).

---

## S1 — Gateway token validation and trusted identity

**Layer:** L1 · **Owner:** Sahar Attia · **Estimate:** 4h · **FR:** FR-04, FR-14

As a customer, I want protected routes to reject requests without a valid Keycloak token so that nobody can act as me.

**Definition of Done:** `curl -i -X POST http://localhost:8080/api/v1/products` without a token returns `401`. A request sent with a forged `X-User-Id` header reaches the service with that header replaced by the subject from the validated JWT. Test `GatewaySecurityTest.stripsClientSuppliedIdentityHeaders` passes.

## S2 — Public product browse with category name

**Layer:** L1 · **Owner:** Ahmed Khalaf · **Estimate:** 4h · **FR:** FR-01, FR-03

As a shopper, I want to browse products page by page without signing in, and see the category name on each product, so that I can find what to buy.

**Definition of Done:** `curl http://localhost:8080/api/v1/products?page=0&size=10` returns `200` with a page of products, no token needed. `GET /api/v1/products/{id}` includes `categoryName`. Flyway migration `V1__create_product_tables.sql` runs in `ProductRepositoryIT` (Testcontainers).

## S3 — Admin product create / update / delete

**Layer:** L1 · **Owner:** Ahmed Khalaf · **Estimate:** 3h · **FR:** FR-02

As an admin, I want to create, update, and delete products so that the catalogue stays correct.

**Definition of Done:** `POST /api/v1/products` with the `admin-test` token returns `201`; the same call with the `customer-test` token returns `403`. Test `ProductControllerTest.customerCannotCreateProduct` passes.

## S4 — Product read cache with eviction

**Layer:** L1 · **Owner:** Hussein Elsaka · **Estimate:** 3h · **FR:** FR-15

As a shopper, I want product pages to load fast and always show the latest data so that I do not see stale prices.

**Definition of Done:** the second `GET /api/v1/products/{id}` is served from Redis (`redis-cli KEYS 'products*'` shows the entry). After an admin `PUT`, both the item and list entries are evicted. Test `ProductCacheIT.updateEvictsItemAndList` passes.

## S5 — Public endpoint rate limiting

**Layer:** L1 · **Owner:** Ahmed Khalaf · **Estimate:** 3h · **FR:** FR-13

As the platform owner, I want each client limited on public endpoints so that one client cannot slow the shop for everyone.

**Definition of Done:** a burst beyond the configured limit from one client returns `429` while a second client still gets `200`, checked once for two signed-in users (keyed by JWT subject) and once for two anonymous IPs. A forged `X-Forwarded-For` header from outside the trusted ingress does not change the key. Test `RateLimitIT.limitsOneClientOnly` passes. Limits are recorded in ADD §7.

## S6 — Internal stock check and admin stock view/adjust

**Layer:** L2 · **Owner:** Ahmed Qamar · **Estimate:** 4h · **FR:** FR-06, FR-12

As an admin, I want to view and adjust stock levels, and as the order service I need to ask whether stock is available, so that orders are only accepted when goods exist.

**Definition of Done:** `GET /api/v1/inventory/check?productId=1&quantity=2` with an `order-service` token returns `{"available": true|false}`; the same call with a `gateway-service` token returns `403`. `PUT /api/v1/inventory/{productId}` through the Gateway with the admin token updates stock. Test `InventoryRepositoryIT` (Testcontainers) passes.

## S7 — Idempotent payment and refund

**Layer:** L2 · **Owner:** Ahmed Qamar · **Estimate:** 4h · **FR:** FR-08

As the business, I want each order charged at most once even when a request is retried, so that customers are never double-charged.

**Definition of Done:** two `POST /api/v1/payments` calls with the same `Idempotency-Key` and a `payment-operator` token return the same payment ID and create one row in `payments`. Test `PaymentServiceTest.sameKeyChargesOnce` passes. An `order-service` token on this endpoint returns `403`.

## S8 — Place order and read own orders

**Layer:** L2 · **Owner:** Sahar Attia · **Estimate:** 4h · **FR:** FR-05, FR-06, FR-10

As a customer, I want my order accepted immediately with an order ID and status `PENDING`, or rejected at once if stock is missing, and I want to see only my own orders, so that I know where I stand and my purchases stay private.

**Definition of Done:** `POST /api/v1/orders` returns `201` with `orderId` and `PENDING` when stock exists; returns `409` when it does not, and no outbox row is written. Unit prices come from Product (`GET /api/v1/products/{id}` with the `order-service` token, ADD §3.4); with Product or Inventory stopped, the call fails fast with `503` through the Resilience4j circuit breaker (`OrderServiceTest.inventoryDownRejectsQuickly`). The order and its outbox row are saved in one transaction (`OrderServiceIT.savesOrderAndOutboxTogether`). `GET /api/v1/orders` returns only the caller's orders, and `GET /api/v1/orders/{id}` for another customer's order returns `404` (`OrderControllerTest.cannotReadOtherCustomersOrder`).

## S9 — Payment from the Saga with retry and circuit breaker

**Layer:** L3 · **Owner:** Ahmed Qamar · **Estimate:** 4h · **FR:** FR-08, FR-09, NFR-01

As the business, I want each reserved order charged exactly once, with a failed payment ending in compensation, so that customers are never double-charged and stock is never kept for a failed payment.

**Definition of Done:** `InventoryReserved` produces exactly one `PaymentCompleted` or `PaymentFailed` through Payment's outbox, with the payment row keyed by `orderId` (ADD §3.4). The simulated charge runs inside Resilience4j Retry and CircuitBreaker; with the failure-rate switch at 100%, retries are exhausted and `PaymentFailed` is published. Delivering `InventoryReserved` twice charges once (`PaymentEventListenerIT.duplicateInventoryReservedChargesOnce`).

## S10 — Stock reservation and release from events

**Layer:** L3 · **Owner:** Ahmed Qamar · **Estimate:** 4h · **FR:** FR-07, NFR-05, NFR-10

As the business, I want stock reserved when an order is placed and released when payment fails so that we never sell stock we do not have or lock stock we do not need.

**Definition of Done:** an `OrderPlaced` event produces `InventoryReserved` or `InventoryReservationFailed`; `PaymentFailed` releases the reservation and publishes `InventoryReleased`. Delivering the same event twice changes stock once (`InventoryEventListenerIT.duplicateOrderPlacedReservesOnce`). No reservation remains more than 30 seconds after `CANCELLED` (query in the test).

## S11 — Saga outcomes and outbox publisher in Order

**Layer:** L3 · **Owner:** Sahar Attia · **Estimate:** 4h · **FR:** FR-09, NFR-01

As a customer, I want my order to end as `CONFIRMED` or `CANCELLED` and never be lost, so that I always get a final answer.

**Definition of Done:** the outbox poller publishes `OrderPlaced`. `PaymentCompleted` → `CONFIRMED`; `InventoryReservationFailed` or `PaymentFailed` → `CANCELLED`. A late or duplicate event for a terminal order changes nothing (`OrderSagaIT.lateEventIsIgnored`). With payment-service stopped, the order stays `PENDING` and completes after restart.

## S12 — Notifications with retry and Dead Letter Topic

**Layer:** L3 · **Owner:** Ahmed Khalaf · **Estimate:** 3h · **FR:** FR-11, NFR-10

As a customer, I want a confirmation when my order is confirmed and a notice when it is cancelled, so that I do not have to keep checking.

**Definition of Done:** `OrderConfirmed` and `OrderCancelled` each produce one log-based notification. A forced send failure is retried by `@RetryableTopic` (configured with `dltTopicSuffix = ".DLT"`, ADD §6 F5), then parked on the `order-events.DLT` topic (`kafka-console-consumer` on the DLT shows the message). Test `NotificationListenerIT.exhaustedRetriesGoToDlt` passes.

## S13 — CI image build and first Helm deploy to kind

**Layer:** L4 · **Owner:** Hussein Elsaka · **Estimate:** 4h · **NFR:** NFR-04, NFR-08

As a maintainer, I want CI to test, build, and push each service image, and a Helm chart to deploy it, so that anyone can run the platform on Kubernetes.

**Definition of Done:** a push to `main` runs the GitHub Actions workflow: tests → image build → push to GHCR with the commit SHA tag. `helm install product-service deployment/helm/product-service` on kind gives a `Running` pod with passing readiness and liveness probes, running as non-root. Further services and ArgoCD are split into follow-up stories at G1.

## S14 — Cross-hop trace and k6 product read test

**Layer:** L5 · **Owner:** Hussein Elsaka · **Estimate:** 4h · **NFR:** NFR-02, NFR-03, NFR-06

As a maintainer, I want one trace across HTTP and Kafka and a measured read latency, so that I can find bottlenecks with evidence.

**Definition of Done:** a placed order shows one trace ID in Zipkin spanning Gateway → Order → Kafka → Inventory. `k6 run k6/load-products.js` reports `GET /api/v1/products` P95 < 200 ms and ≥ 50 req/s through the Gateway. Results go in `docs/PERFORMANCE-REPORT.md`.

## S15 — Submit and read product reviews (B1 Core)

**Layer:** L6 · **Owner:** Ahmed Qamar · **Estimate:** 4h · **FR:** FR-16

As a shopper, I want to submit one rating and review per product and see other shoppers' reviews, so that I can decide with social proof.

**Definition of Done:** `POST /api/v1/products/{productId}/reviews` with the customer token returns `201`; a second review by the same customer for the same product returns `409`. `GET /api/v1/products/{productId}/reviews?page=0&size=10` works without a token. Product detail shows `averageRating` and `reviewCount`, updated through `ReviewSubmitted`; delivering the same event twice changes the count once (`ProductRatingProjectionIT.duplicateEventCountedOnce`). An event for an unknown product is recorded in `processed_event` and changes nothing (`ProductRatingProjectionIT.unknownProductIsSkipped`, ADD §2).
