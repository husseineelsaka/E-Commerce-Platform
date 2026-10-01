# order-service

Places orders and serves customers their own orders, in `order_db`. Port 8082. Story S8 (FR-05, FR-06, FR-10). Saga participation is story S11.

## Prerequisites

- Infrastructure from `deployment/docker` running (PostgreSQL, Keycloak), Config Server, Eureka, product-service, and inventory-service.
- Environment: `ORDER_DB_PASSWORD` and `ORDER_CLIENT_SECRET` (no defaults). `ORDER_DB_HOST` defaults to `localhost` and `ORDER_DB_PORT` to `5432` (match `POSTGRES_HOST_PORT` in `deployment/docker/.env`). `KEYCLOAK_ISSUER_URI` defaults to `http://localhost:8180/realms/ecommerce-platform`.

## Run

```sh
mvn -pl services/order-service spring-boot:run
```

## Placing an order

`POST /api/v1/orders` with `{"items": [{"productId": 1, "quantity": 2}]}`:

1. For every item, Order reads the current price from product-service (`GET /api/v1/products/{id}`) and checks stock with inventory-service (`GET /api/v1/inventory/check`), both over OpenFeign with an `order-service` Client Credentials token (ADD §3.4).
2. Each call is protected by Resilience4j CircuitBreaker, Retry (2 attempts), Bulkhead (20 concurrent calls), and TimeLimiter (500 ms), configured in `src/main/resources/application.yml`. An unknown product (404) is neither retried nor counted by the circuit breaker.
3. Only when every item is priced and in stock does Order save the order (`PENDING`), its items with the frozen unit price, and an `OrderPlaced` outbox row in **one** local transaction. No remote call runs inside that transaction.

| Outcome | Status |
| --- | --- |
| Accepted | 201 `{"orderId","status":"PENDING"}` with `Location` |
| Invalid body, duplicate `productId`, or unknown product | 400 |
| Any item out of stock | 409, nothing saved |
| Product or Inventory unavailable, slow, or circuit open | 503, nothing saved |

## Saga (Kafka)

- **Outbox publisher:** every 500 ms (`outbox.publisher.poll-interval`) unpublished `outbox_event` rows are sent to `order-events`, keyed by `orderId`, then marked published. Rows are locked with `FOR UPDATE SKIP LOCKED`; a failed send is retried on the next poll.
- **Outcomes:** Order consumes `inventory-events` and `payment-events`. `PaymentCompleted` → `CONFIRMED` and `OrderConfirmed`; `PaymentFailed` or `InventoryReservationFailed` → `CANCELLED` and `OrderCancelled` with the reason. Other event types are ignored.
- **Exactly one outcome:** the order changes only from `PENDING` (`UPDATE … WHERE status = 'PENDING'`); each `eventId` is recorded in `processed_event` in the same transaction, so duplicate and late events change nothing.
- A record that still fails after 3 retries is parked on `<topic>.DLT`.
- `KAFKA_BOOTSTRAP_SERVERS` defaults to `localhost:9092`.

## Reading orders

| Method and path | Result |
| --- | --- |
| `GET /api/v1/orders?page&size` | The caller's own orders, newest first (`size` 1–100) |
| `GET /api/v1/orders/{id}` | 200 `{"orderId","status","totalAmount","createdAt","items"}`; another customer's order or unknown id → 404 |

All order endpoints accept only the Gateway (`gateway-service` token) with the `CUSTOMER` role; the customer id comes from the Gateway's `X-User-Id` header, never from the request body.

Example through the Gateway (bash, variables from `deployment/docker/.env`):

```sh
TOKEN=$(curl -s http://localhost:8180/realms/ecommerce-platform/protocol/openid-connect/token \
  -d grant_type=password -d client_id=user-sign-in -d username=customer-test \
  --data-urlencode password="$CUSTOMER_USER_PASSWORD" | python -c "import sys,json;print(json.load(sys.stdin)['access_token'])")
curl -i -X POST http://localhost:8080/api/v1/orders -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"items":[{"productId":1,"quantity":2}]}'
curl http://localhost:8080/api/v1/orders -H "Authorization: Bearer $TOKEN"
```

## Tests

```sh
mvn -pl services/order-service -am verify
```

`OrderControllerTest` covers ownership and caller rules (`cannotReadOtherCustomersOrder`); `OrderServiceTest` uses stubbed Product and Inventory HTTP services (`inventoryDownRejectsQuickly`, out of stock, unknown product without retry); `OrderServiceIT.savesOrderAndOutboxTogether` runs on Testcontainers PostgreSQL (Docker required) and also proves a failed outbox insert leaves no order; `OrderSagaIT` runs on Testcontainers PostgreSQL and Kafka (outbox publishing, outcomes, `lateEventIsIgnored`, duplicate events).
