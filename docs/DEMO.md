# Final demo script (15 minutes)

Every command below was run on Docker Compose on 2026-10-03; the "Expect" lines are what it printed. Run everything in
**Git Bash from the repository root**. Commands that start with `$` variables need `source scripts/demo-env.sh` first.

| Time | Part | Requirement shown |
| --- | --- | --- |
| 0:00 | Architecture | ADD, diagrams |
| 1:30 | Security, rate limit, cache | L1: FR-01–FR-04, FR-13, NFR-02 |
| 4:00 | Place an order: Saga, trace, notification | L2–L3: FR-07–FR-10, NFR-06 |
| 7:00 | Payment declined: compensation | L3: FR-09, NFR-01 |
| 8:30 | Inventory down: circuit breaker | L2: FR-11 |
| 10:00 | Reviews and ratings | L6: B1 |
| 12:00 | Observability and performance | L5 |
| 13:30 | CI, Helm, ArgoCD; API docs | L4 |
| 15:00 | End | |

## Before the audience arrives (10 minutes earlier)

```sh
docker stop ecommerce-control-plane                                    # kind and Compose together overload the laptop
docker compose -f deployment/docker/docker-compose.yml --env-file deployment/docker/.env up -d --wait
source scripts/demo-env.sh                                             # Expect: tokens ready (customer … chars, admin … chars)
ready                                                                  # Expect: platform ready
```

`ready` can take 1–2 minutes after a fresh start, while the services register in Eureka. Open these tabs:

- `docs/architecture/01-service-boundaries.svg` and `02-order-flow-sequence.svg`
- Zipkin `http://localhost:9411`
- Grafana `http://localhost:3000` (user `admin`, password `GRAFANA_ADMIN_PASSWORD` in `deployment/docker/.env`), dashboard **E-Commerce Platform**, last 15 minutes
- Swagger UI `http://localhost:8089`
- GitHub: the repository's **Actions** tab and a merged pull request

Tokens expire after 5 minutes. **Run `tokens` at the start of every part.**

## 0:00 Architecture (1.5 min)

Show `01-service-boundaries.svg`, then `02-order-flow-sequence.svg`. The points to make:

- Nine Spring Boot applications, each with its own PostgreSQL database.
- Only the Gateway is public, on port 8080.
- Order checks price and stock synchronously over Feign with a circuit breaker.
- The rest of the order is a choreography Saga over Kafka, using a transactional outbox and idempotent consumers.

## 1:30 Security, rate limit, cache (2.5 min)

```sh
tokens
curl -s "$GW/api/v1/products?page=0&size=3" | pp                      # public read: 200, page of products
curl -s -o /dev/null -w "%{http_code}\n" -X POST $GW/api/v1/products -H "Content-Type: application/json" \
  -d '{"name":"Demo Lamp","price":25.00,"categoryId":2}'               # Expect: 401 (no token)
curl -s -o /dev/null -w "%{http_code}\n" -X POST $GW/api/v1/products -H "Authorization: Bearer $CUSTOMER" \
  -H "Content-Type: application/json" -d '{"name":"Demo Lamp","price":25.00,"categoryId":2}'   # Expect: 403 (customer)
LAMP=$(curl -s -X POST $GW/api/v1/products -H "Authorization: Bearer $ADMIN" -H "Content-Type: application/json" \
  -d '{"name":"Demo Lamp","price":25.00,"categoryId":2}' | field id); echo "LAMP=$LAMP"     # Expect: LAMP=<new id> (201)
```

Say: the Gateway validates the Keycloak token and removes any `X-User-*` header the client sent. It then injects the real identity and calls the service with its own client token. Every service checks the token again (zero trust).

Rate limit (anonymous: 100 requests/s per IP, burst 200):

```sh
docker run --rm --network docker_default -v "$(pwd -W)/k6:/scripts" -e BASE_URL=http://api-gateway:8080 \
  grafana/k6:1.3.0 run --quiet /scripts/rate-limit-demo.js
```

Expect, under `checks`: a share served (200) and the rest rate limited (429). The split depends on how fast the laptop sends: it measured 80/20 and 35/65.

Cache (Redis, cache-aside):

```sh
docker exec docker-redis-1 redis-cli --scan --pattern 'products*'    # Expect: products:item::1 (and list pages)
```

## 4:00 Place an order: Saga, trace, notification (3 min)

```sh
tokens
curl -s $GW/api/v1/inventory/1 -H "Authorization: Bearer $ADMIN" | pp            # note available / reserved
ORDER=$(curl -s -X POST $GW/api/v1/orders -H "Authorization: Bearer $CUSTOMER" -H "Content-Type: application/json" \
  -d '{"items":[{"productId":1,"quantity":2}]}' | field orderId); echo "ORDER=$ORDER"   # 201, saved as PENDING
curl -s $GW/api/v1/orders/$ORDER -H "Authorization: Bearer $CUSTOMER" | pp        # Expect: "status": "CONFIRMED" (about 1 s)
curl -s $GW/api/v1/inventory/1 -H "Authorization: Bearer $ADMIN" | pp            # Expect: available -2, reserved +2
```

Say the event path:
1. Order writes `OrderPlaced` to its outbox in the same transaction as the order.
2. Inventory reserves the stock and publishes `InventoryReserved`.
3. Payment charges and publishes `PaymentCompleted`.
4. Order sets the status to CONFIRMED.

Every consumer ignores duplicate events.

One trace across HTTP and Kafka:

```sh
TRACE=$(docker exec docker-postgres-1 psql -U postgres -d order_db -tAc \
  "SELECT split_part(trace_parent,'-',2) FROM outbox_event WHERE aggregate_id='$ORDER' AND event_type='OrderPlaced'")
echo "http://localhost:9411/zipkin/traces/$TRACE"                                 # open it: 23 spans, 6 services
```

The customer is notified, and the log line carries the same trace ID:

```sh
$COMPOSE logs notification-service | grep "$ORDER" | cut -d'|' -f2- | python -c "import sys,json; [print(json.loads(l)['message']) for l in sys.stdin]"
# Expect: NOTIFICATION to customer …: Your order … is confirmed.
```

## 7:00 Payment declined: compensation (1.5 min)

```sh
PAYMENT_FAILURE_RATE=1.0 $COMPOSE up -d --wait payment-service                    # about 30 s: every charge now declines
tokens
curl -s $GW/api/v1/inventory/3 -H "Authorization: Bearer $ADMIN" | pp            # note the numbers
ORDER=$(curl -s -X POST $GW/api/v1/orders -H "Authorization: Bearer $CUSTOMER" -H "Content-Type: application/json" \
  -d '{"items":[{"productId":3,"quantity":1}]}' | field orderId); echo "ORDER=$ORDER"
curl -s $GW/api/v1/orders/$ORDER -H "Authorization: Bearer $CUSTOMER" | pp        # Expect: "status": "CANCELLED"
curl -s $GW/api/v1/inventory/3 -H "Authorization: Bearer $ADMIN" | pp            # Expect: same numbers as before (released)
$COMPOSE logs notification-service | grep "$ORDER" | cut -d'|' -f2- | python -c "import sys,json; [print(json.loads(l)['message']) for l in sys.stdin]"
# Expect: … was cancelled: Payment declined.
$COMPOSE up -d --wait payment-service                                             # back to normal, about 30 s
```

Say what happens: Payment retries 3 times behind a circuit breaker and then publishes `PaymentFailed`. Inventory releases the reservation, and Order cancels the order.

Optional, if time allows: while payment-service is stopped (`$COMPOSE stop payment-service`), a new order stays `PENDING`. After `$COMPOSE start payment-service` it becomes `CONFIRMED` about 25 s later, because the events waited in Kafka.

## 8:30 Inventory down: circuit breaker (1.5 min)

```sh
$COMPOSE stop inventory-service
tokens
for i in 1 2 3 4 5; do curl -s -o /dev/null -w "try $i: %{http_code} in %{time_total}s\n" -X POST $GW/api/v1/orders \
  -H "Authorization: Bearer $CUSTOMER" -H "Content-Type: application/json" -d '{"items":[{"productId":1,"quantity":1}]}'; done
```

Expect:
- Tries 1–2: `503 in 1.1s` (retry, then failure)
- From try 3: `503 in 0.09s`, because the circuit is open and Order fails fast instead of waiting

```sh
docker exec docker-order-service-1 curl -s localhost:8082/actuator/prometheus | grep 'circuitbreaker_state{.*inventory.*state="open"'
# Expect: … name="inventory",state="open"} 1.0
$COMPOSE start inventory-service
ready                                                                             # Expect: platform ready (about 15 s)
```

## 10:00 Reviews and ratings: B1 (2 min)

Uses the Demo Lamp created at 1:30. If the variable was lost, create it again with the `LAMP=` command from that part.

```sh
tokens
curl -s -X POST $GW/api/v1/products/$LAMP/reviews -H "Authorization: Bearer $CUSTOMER" -H "Content-Type: application/json" \
  -d '{"rating":5,"text":"Bright and warm light."}' | pp                          # Expect: 201 with reviewId
curl -s -X POST $GW/api/v1/products/$LAMP/reviews -H "Authorization: Bearer $CUSTOMER" -H "Content-Type: application/json" \
  -d '{"rating":1,"text":"Changed my mind."}' | pp                                # Expect: 409 already reviewed
curl -s "$GW/api/v1/products/$LAMP/reviews?page=0&size=10" | pp                  # public read, newest first
curl -s $GW/api/v1/products/$LAMP | pp                                            # Expect: "averageRating": 5.0, "reviewCount": 1
```

Say:
- review-service has its own database.
- `ReviewSubmitted` goes through its outbox to Kafka.
- Product keeps the rating as a sum and a count; the average is computed on read, so it never drifts.
- A duplicate event is counted once (`ProductRatingProjectionIT.duplicateEventCountedOnce`).

## 12:00 Observability and performance (1.5 min)

Start a one-minute load and switch to the Grafana dashboard while it runs:

```sh
docker run --rm --network docker_default -v "$(pwd -W)/k6:/scripts" -e BASE_URL=http://api-gateway:8080 -e DURATION=60s \
  grafana/k6:1.3.0 run --quiet /scripts/load-products.js
```

Expect: ✓ `p(95)<200` for both `list` and `product`, and about 75 req/s. Show the panels "Requests per second" and "P95 latency".

Then open `docs/PERFORMANCE-REPORT.md` and walk through the bottleneck story:
- **Finding:** the Gateway added about 430 ms at 800 req/s. A JFR profile pointed to Spring Security observations and a per-request Lua script check.
- **Fix:** disabled those observations and loaded the script once.
- **Result:** P95 at 800 req/s went from 518 ms to 18.8 ms, and Gateway CPU per request fell by 27 %.

## 13:30 CI, Helm, ArgoCD; API docs (1.5 min)

- **GitHub Actions tab.** Every pull request runs `mvn verify` (unit and Testcontainers tests, 60 % coverage gate) and a check that every application has a Helm chart. It then builds nine images; pushes to `main` publish them to GHCR tagged with the commit SHA.
- **Helm and ArgoCD.** `deployment/helm/` has one chart per service; `deployment/argocd/applications.yaml` has nine Applications with automated sync and self-heal. Evidence: `docs/phases/L4.md` and `L6.md` (9/9 Synced/Healthy at `4c151d2`).
  - Live option: after the demo, `$COMPOSE stop` the apps (not the infrastructure), then `docker start ecommerce-control-plane`.
  - Show `kubectl --context kind-ecommerce -n argocd get applications` and `kubectl --context kind-ecommerce -n ecommerce get pods`.
- **Swagger UI.** `http://localhost:8089`, every endpoint. The Postman collection in `docs/api/postman` runs 46 requests (`docs/api/README.md`).

## After the demo

```sh
tokens
curl -s -o /dev/null -w "%{http_code}\n" -X DELETE $GW/api/v1/products/$LAMP -H "Authorization: Bearer $ADMIN"   # Expect: 204
```

## If something goes wrong

| Symptom | Fix |
| --- | --- |
| `KeyError: 'access_token'` or `401` on every call | `tokens` (expired) |
| `503 Product unavailable` / `Inventory unavailable` right after a start or restart | `ready` (services still registering in Eureka) |
| `KeyError: 'orderId'` | print the answer: run the same `curl` without `\| field orderId` |
| Everything slow, `docker stats` shows `ecommerce-control-plane` | `docker stop ecommerce-control-plane` |
| An order stays `PENDING` | `$COMPOSE ps payment-service` — start it if it is stopped |
