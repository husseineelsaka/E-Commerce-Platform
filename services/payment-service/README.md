# payment-service

Simulated, idempotent payments in `payment_db`. Port 8083. Story S7 (FR-08). The Saga charge path (Kafka, Resilience4j) is story S9.

## Prerequisites

- Infrastructure from `deployment/docker` running (PostgreSQL, Keycloak), Config Server, and Eureka.
- Environment: `PAYMENT_DB_PASSWORD` (no default). `PAYMENT_DB_HOST` defaults to `localhost` and `PAYMENT_DB_PORT` to `5432` (match `POSTGRES_HOST_PORT` in `deployment/docker/.env`). `KEYCLOAK_ISSUER_URI` defaults to `http://localhost:8180/realms/ecommerce-platform`.
- `PAYMENT_FAILURE_RATE` (0.0–1.0, default 0.0): probability that the simulated charge fails. A failed charge is stored with status `FAILED`.

## Run

```sh
mvn -pl services/payment-service spring-boot:run
```

## Endpoints

Internal only: there is no Gateway route. Callers need a token from the `payment-operator` client with `aud` `payment-service`; any other client gets 403, a token without that audience gets 401.

| Method and path | Result |
| --- | --- |
| `POST /api/v1/payments`, header `Idempotency-Key` (1–100 chars), body `{"orderId": UUID, "amount": decimal > 0, ≤ 2 decimals}` | First call: charge once, 201 `{"paymentId","orderId","amount","status"}` with `Location`. Same key and body: 200 with the stored payment, no second charge. Same key, different body: 422. Order already paid under another key: 409. Missing or invalid key: 400. |
| `POST /api/v1/payments/{id}/refund` | 200 with status `REFUNDED`; unknown payment: 404; not `COMPLETED` (failed or already refunded): 409 |

The key and the order id are both `UNIQUE` in PostgreSQL, so two concurrent requests with the same key still store one payment and charge once.

Idempotency demo (bash, variables from `deployment/docker/.env`):

```sh
TOKEN=$(curl -s http://localhost:8180/realms/ecommerce-platform/protocol/openid-connect/token \
  -d grant_type=client_credentials -d client_id=payment-operator \
  -d client_secret="$PAYMENT_OPERATOR_CLIENT_SECRET" | python -c "import sys,json;print(json.load(sys.stdin)['access_token'])")
ORDER=$(python -c "import uuid;print(uuid.uuid4())")
for i in 1 2; do
  curl -s -w ' -> %{http_code}\n' -X POST http://localhost:8083/api/v1/payments \
    -H "Authorization: Bearer $TOKEN" -H 'Idempotency-Key: demo-1' -H 'Content-Type: application/json' \
    -d "{\"orderId\":\"$ORDER\",\"amount\":49.90}"
done
```

The first call returns 201 and the second returns 200 with the same `paymentId`.

## Tests

```sh
mvn -pl services/payment-service -am verify
```

`PaymentControllerTest` covers the HTTP contract and caller rules; `PaymentServiceTest.sameKeyChargesOnce` proves one charge per key; `PaymentServiceIT` uses Testcontainers PostgreSQL (Docker required) for the unique constraints, concurrent duplicates, and refund rules.
