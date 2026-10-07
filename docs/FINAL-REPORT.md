# Final Report — Team 1

**Product:** Enterprise E-Commerce Platform · **Bonus:** B1 Product Reviews & Ratings (Core) · **Repository:** [github.com/husseineelsaka/E-Commerce-Platform](https://github.com/husseineelsaka/E-Commerce-Platform)
**Stack:** Java 21, Spring Boot 3.5.16, Spring Cloud 2025.0.3 · **Gates:** L0–L6 all Green

This report summarizes what Team 1 built, how it maps to the Trainee Handbook's requirements, and the evidence behind each claim. The details live in the documents it links: the [ADD](adr/ADD-team-1.md), the [phase documents](phases/), and the [Performance Report](PERFORMANCE-REPORT.md).

## 1. Team and ownership

| Member | Owned (ADD, drawing, stories) |
| --- | --- |
| Hussein Elsaka | L0 platform and infrastructure; ADD §7–§8; S4 cache, S13 CI/Helm/kind, S14 tracing and k6; L4/L5 |
| Sahar Attia | ADD §3–§4; drawing 3 (data locations); S1 Gateway security, S8 place order and own orders, S11 Saga in Order |
| Ahmed Khalaf | ADD §5–§6; drawing 2 (order flow); S2 product browse, S3 admin product writes, S5 rate limiting, S12 notifications |
| Ahmed Qamar | ADD §1–§2; drawing 1 (service boundaries); S6 inventory, S7 payment API, S9 payment from the Saga, S10 reservation/release, S15 reviews (B1) |

Every change went through a pull request with a teammate review. Commits follow `capstone-Lx: short-description` and credit the task owner with a `Co-authored-by:` trailer (AGENTS.md §10).

## 2. What we built

Nine independent Spring Boot applications in one Maven monorepo. They share only the parent POM and no service depends on another service's Java module.

| Application | Port | Store | Responsibility |
| --- | --- | --- | --- |
| config-server | 8888 | `config-repo/` (native) | Central configuration |
| eureka-server | 8761 | — | Service discovery |
| api-gateway | 8080 | Redis (rate limit) | The only entry point: JWT validation, role rules, identity headers, Client Credentials token swap, rate limiting |
| product-service | 8081 | PostgreSQL + Redis cache | Catalogue CRUD, category-name read model, rating projection (B1) |
| order-service | 8082 | PostgreSQL + outbox | Place order (Feign price lookup and stock check with Resilience4j), Saga outcomes, own orders |
| payment-service | 8083 | PostgreSQL + outbox | Idempotent simulated charge with Retry/CircuitBreaker, refund, failure-rate switch |
| inventory-service | 8084 | PostgreSQL + outbox | Stock check, reserve, release, admin view/adjust, low-stock query |
| notification-service | 8085 | — | Confirmation and cancellation notices, `@RetryableTopic` and DLT |
| review-service | 8086 | PostgreSQL + outbox | B1: one review per customer per product, paginated public reads, `ReviewSubmitted` |

Infrastructure is exactly the handbook list: PostgreSQL, Kafka with Zookeeper, Redis, Keycloak, Zipkin, Prometheus, and Grafana. We added none.

**Two paths.** The synchronous path (Gateway → Order → Product/Inventory over OpenFeign) answers "can I accept this order now?". The asynchronous path is a choreography Saga over `order-events`, `inventory-events`, and `payment-events`. It carries the business transaction after the customer already holds `orderId` + `PENDING`.

**Security.** The Gateway validates the user's Keycloak JWT and strips any client-sent `X-User-*` header. It then forwards the user as `X-User-Id` / `X-User-Roles` with its own `gateway-service` Client Credentials token. Every service checks issuer, audience, and the calling client (`azp`) per endpoint. The stock check and the Payment API have no public route. Actuator runs on a separate management port. Drawings: [docs/architecture](architecture/README.md).

## 3. Requirements traceability

| Requirement | Delivered | Evidence |
| --- | --- | --- |
| FR-01 public paginated browse | `GET /api/v1/products` | [L1](phases/L1.md) evidence: anonymous list 200 with `categoryName` |
| FR-02 admin product CRUD | `POST/PUT/DELETE /api/v1/products` (ADMIN) | L1: 201/200/204; customer → 403 |
| FR-03 category-name read model | `ProductView` JPQL projection | L1: detail includes `categoryName` |
| FR-04 Keycloak sign-in, protected routes | Gateway resource server | L1: no token → 401, wrong role → 403 |
| FR-05 immediate `orderId` + `PENDING` | `POST /api/v1/orders` → 201 | [L2](phases/L2.md) |
| FR-06 sync stock check, no payment on failure | Feign + CircuitBreaker, Retry, Bulkhead, TimeLimiter | L2: product 20 → 409; Inventory down → 503 in ~0.1 s once the circuit opened |
| FR-07 reserve and release | Inventory all-or-nothing reservation | [L3](phases/L3.md): reservation `RELEASED`, 0 `RESERVED` rows after cancel |
| FR-08 pay exactly once | `UNIQUE` on `idempotency_key` and `order_id` | L2: repeated key → same `paymentId`, one row |
| FR-09 `CONFIRMED` / `CANCELLED` + compensation | Saga outcomes, conditional update from `PENDING` | L3: confirmed in ~1 s; `PAYMENT_FAILURE_RATE=1.0` → cancelled, stock restored |
| FR-10 read own order, list own orders | ownership from the Gateway identity | L2; another customer's order → 404 |
| FR-11 notices, retries, DLT | `@RetryableTopic` → `order-events.DLT` | L3 |
| FR-12 admin stock view/adjust | `GET/PUT /api/v1/inventory/{productId}`, plus `GET /api/v1/inventory/low-stock` | L2, [L6](phases/L6.md) |
| FR-13 per-client rate limiting | Redis token bucket; subject or client IP | L1: one client 429 while another gets 200 |
| FR-14 Client Credentials between services | `gateway-service`, `order-service`, `payment-operator` clients | L1/L2: wrong client or audience → 401/403 |
| FR-15 cache with eviction | Redis cache-aside, evicted after commit | L1: no `products*` key left after a write |
| FR-16 Bonus B1 Core | `review-service` + rating projection in Product | L6: 201 / 409 / 400 / 403 / 401; rating updated in 0.7 s |
| NFR-01 Payment down, no lost orders | events wait in Kafka; order stays `PENDING` | L3: `PENDING` for 25 s, `CONFIRMED` ~4 s after restart |
| NFR-02 latency | cached GET P95 8.1 ms; order POST P95 61.6 ms at 20 VUs | [Performance Report](PERFORMANCE-REPORT.md) |
| NFR-03 throughput ≥ 50 req/s | 75 req/s under the default limit; 800 req/s with it raised | Performance Report |
| NFR-04 security | no secrets in Git, JWT at the Gateway, non-root images, one ServiceAccount per app | [L4](phases/L4.md) |
| NFR-05 no orphaned reservation > 30 s | release in the same transaction as the `PaymentFailed` handling | L3 and `InventoryEventListenerIT` |
| NFR-06 one trace across HTTP and Kafka | `traceparent` stored in each outbox row | [L5](phases/L5.md): 23 spans across 6 apps; JSON logs carry `traceId` |
| NFR-07 ≥ 60 % service-layer coverage, Testcontainers | JaCoCo `check` fails the build below 60 % | §5 below |
| NFR-08 Compose and Helm | `docker compose up`; one Helm chart per app; ArgoCD | L4: 16/16 containers healthy; 8/8, later 9/9, Applications `Synced` / `Healthy` |
| NFR-09 Flyway, `/api/v1` | every database-owning service | code review |
| NFR-10 at-least-once + idempotent consumers + DLT | `processed_event` per consumer; `<topic>.DLT` | L3: duplicate events change nothing |

## 4. Key decisions

All handbook defaults were kept; ADD §7 records "Replaced defaults: none". The decisions below are where we chose among the allowed options. Each has its full DECISION / OPTIONS / REASON / TRADE-OFF / REVISIT entry in the ADD or a phase document.

| Decision | Why | Where |
| --- | --- | --- |
| Choreography Saga, transactional outbox, idempotent consumers | three participants, a linear flow, no coordinator to keep alive | ADD §5 |
| Gateway swaps the user token for its own Client Credentials token | services trust identity headers only from one client | L1, ADD §7 |
| Payment idempotency by database `UNIQUE` constraints, row inserted before the charge | constraints hold across concurrent requests and instances; a losing duplicate fails without charging | L2 |
| Inventory locks an order's stock rows in product-id order | all-or-nothing reservation without deadlocks | L3 |
| Payment down → order stays `PENDING`, no timeout | NFR-01 allows it; a deadline would need an Order-owned cancel-and-refund | ADD §6 F3 |
| Outbox rows carry the W3C `traceparent` | one trace across HTTP and Kafka | L5 |
| B1 as its own service with a rating projection in Product | keeps review writes off the catalogue database and product reads fast | ADD §1–§2 |
| On kind, infrastructure stays in Compose and the kind node joins its network | fits a laptop; the cluster runs only the applications | L4 |

## 5. Quality and tests

Measured with `mvn -B verify` on 2026-10-07 (BUILD SUCCESS, 10 min, Docker for Testcontainers). Coverage is JaCoCo line coverage of each service's `*.service` package, the scope of the NFR-07 `check` rule.

| Module | Unit / web tests | Integration tests (Testcontainers) | Service-layer line coverage |
| --- | --- | --- | --- |
| api-gateway | 14 | 3 (Redis) | no `service` package |
| product-service | 22 | 16 | 93.7 % |
| order-service | 9 | 8 | 87.0 % |
| payment-service | 12 | 10 | 87.9 % |
| inventory-service | 17 | 9 | 91.5 % |
| notification-service | 0 | 4 (Kafka) | 93.3 % |
| review-service | 8 | 5 | 92.2 % |
| **Total** | **82** | **55** | every service above the 60 % gate |

Config Server and Eureka have no tests of their own; `scripts/verify-l0.sh` and the Compose health checks verify them. Every database-owning service has at least one Testcontainers test against real PostgreSQL with its Flyway migrations (NFR-07).


- **Kinds of tests:** unit and `@WebMvcTest` tests for role, audience, and ownership rules; Testcontainers integration tests with real PostgreSQL, Kafka, and Redis (no H2) and the real Flyway migrations; Kafka tests for duplicates, compensation, and the DLT.
- **Bugs the tests found:** `*IT` tests never ran because Failsafe was not bound ([L1](phases/L1.md)); the second of two concurrent same-key payments answered 409 instead of the stored payment (`PaymentServiceIT.concurrentSameKeyChargesOnce`, [L2](phases/L2.md)); Resilience4j settings in `config-repo` were silently replaced by defaults in tests (L2).
- **API checks:** a Postman collection with a request for every endpoint ([docs/api](api/README.md)); the recorded run before the low-stock request was added had 46 requests, 62 assertions, 0 failures. `k6/smoke-test.js` passed 130/130 checks after L6.
- **CI:** every pull request runs `mvn -B verify` (tests and the coverage gate), checks that every application has a Helm chart, and builds all nine images. Pushes to `main` publish the images to GHCR tagged with the commit SHA.

## 6. Performance

The Gateway was the bottleneck: at 800 req/s its server P95 was 458 ms while product-service answered in 28 ms. A JFR profile traced the cost to Spring Security observations and a per-request rate-limiter script check. After removing both, the Gateway used 27 % less CPU per request and the stress P95 at 800 req/s fell from 518 ms to 18.8 ms with 0 errors. All NFR-02 and NFR-03 targets are met. Method, data, and limits: [Performance Report](PERFORMANCE-REPORT.md).

## 7. Deviations from the handbook and the plan

| Deviation | Reason | Recorded in |
| --- | --- | --- |
| Repository built from scratch instead of the Starter Repo template | no template repository was available; the layout and asset set are the same | AGENTS.md §1 |
| Architecture drawings are SVG files, not photographed paper | reviewable in PRs and kept in step with the ADD | [L0](phases/L0.md) |
| On kind, PostgreSQL, Kafka, Redis, and Keycloak run in Compose | laptop resources; the kind node joins the Compose network | L4 |
| Spring Cloud 2025.0.3 is past open-source support | it is the line that matches Boot 3.5.x, which the handbook fixes | ADD §7 |

## 8. Lessons learned and the debt we left on purpose

| Member | If we started again | Debt left consciously | We would pay it when |
| --- | --- | --- | --- |
| Ahmed Qamar | Write the concurrency test first: it found the payment race | Reviews for unknown products stay in `review_db` | the verified-purchase stretch goal (Feign to Order) |
| Sahar Attia | Configuration in `config-repo` is invisible to tests; keep test-relevant settings in the service | Keycloak realm changes on an existing volume need a manual admin-CLI step | a tool that applies realm changes (keycloak-config-cli) |
| Ahmed Khalaf | At-least-once delivery with idempotent consumers beats chasing exactly-once | No notification deduplication; no `processed_event` cleanup | notices cost money, or `processed_event` inserts slow down |
| Hussein Elsaka | Measure before optimising; point ArgoCD at `main` from day one | No memory limits in Compose; 100 % trace sampling | before any shared or cloud deployment |

## 9. Where to find everything

| Document | Contents |
| --- | --- |
| [README](../README.md) | How to run the platform, curl examples, requirements checklist |
| [ADD](adr/ADD-team-1.md) | Architecture decisions, API and event contracts, data model, failure modes, security, test plan |
| [Phase documents](phases/) | Decisions, tests, dated evidence, and gate status for L0–L6 |
| [Backlog](BACKLOG.md) · [Team Charter](TEAM-CHARTER.md) | 15 stories with owners; the working agreement |
| [Performance Report](PERFORMANCE-REPORT.md) | k6 results and the bottleneck fix |
| [Demo script](DEMO.md) | The 15-minute final demo |
| [API docs](api/README.md) | OpenAPI, Swagger UI, Postman collection |
