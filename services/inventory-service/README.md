# inventory-service

Owns stock levels in `inventory_db`. Port 8084. Story S6 (FR-06, FR-12).

## Prerequisites

- Infrastructure from `deployment/docker` running (PostgreSQL, Keycloak), Config Server, and Eureka.
- Environment: `INVENTORY_DB_PASSWORD` (no default). `INVENTORY_DB_HOST` defaults to `localhost` and `INVENTORY_DB_PORT` to `5432` (match `POSTGRES_HOST_PORT` in `deployment/docker/.env`). `KEYCLOAK_ISSUER_URI` defaults to `http://localhost:8180/realms/ecommerce-platform`.

## Run

```sh
mvn -pl services/inventory-service spring-boot:run
```

Flyway creates `stock` and seeds 1000 units for demo products 1–19 and 0 for product 20.

## Endpoints

Every request needs a token whose `aud` contains `inventory-service`.

| Method and path | Caller | Result |
| --- | --- | --- |
| `GET /api/v1/inventory/check?productId&quantity` | `order-service` only, no Gateway route | 200 `{"available": true|false}`; unknown product → `false`; `quantity < 1` or missing parameter → 400 |
| `GET /api/v1/inventory/{productId}` | Gateway with `ADMIN` | 200 `{"productId","available","reserved"}`; unknown → 404 |
| `PUT /api/v1/inventory/{productId}` body `{"available": n}` (n ≥ 0) | Gateway with `ADMIN` | 200 with the same shape; creates the stock row if missing |

Admin example through the Gateway (bash, variables from `deployment/docker/.env`):

```sh
TOKEN=$(curl -s http://localhost:8180/realms/ecommerce-platform/protocol/openid-connect/token \
  -d grant_type=password -d client_id=user-sign-in -d username=admin-test \
  --data-urlencode password="$ADMIN_USER_PASSWORD" | python -c "import sys,json;print(json.load(sys.stdin)['access_token'])")
curl -X PUT http://localhost:8080/api/v1/inventory/1 -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"available": 50}'
```

## Tests

```sh
mvn -pl services/inventory-service -am verify
```

`InventoryControllerTest` covers caller and role rules and validation; `InventoryRepositoryIT` runs Flyway on Testcontainers PostgreSQL (Docker required).
