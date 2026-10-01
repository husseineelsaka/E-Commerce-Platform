# Product service

Product catalogue reads use PostgreSQL `product_db`. Flyway creates `category` and `product` and seeds 20 demo products. The service listens on port 8081.

## Run

Use Java 21 or newer and Maven. Start PostgreSQL with the `product_db` database and `product_owner` role from `deployment/docker/postgres/init-databases.sh`; then start Config Server from `config-repo/`, Eureka, and the Gateway. Keycloak must serve the `ecommerce-platform` realm and issue service tokens with `product-service` in `aud`.

Set `PRODUCT_DB_PASSWORD` in the environment. `PRODUCT_DB_HOST` defaults to `localhost` and `PRODUCT_DB_PORT` to `5432` (match `POSTGRES_HOST_PORT` in `deployment/docker/.env`); `KEYCLOAK_ISSUER_URI` defaults to `http://localhost:8180/realms/ecommerce-platform`. No password default is provided.

From the repository root:

```powershell
$env:PRODUCT_DB_PASSWORD = '<local product_owner password>'
mvn -pl services/product-service spring-boot:run
```

## Reads

Through the Gateway on port 8080, these routes are public to shoppers and need no shopper sign-in. The Gateway supplies its own service token. Direct calls to product-service require a valid token issued by the configured issuer with `product-service` in `aud`.

| Endpoint | Response | Allowed service client (`azp`) |
| --- | --- | --- |
| `GET /api/v1/products?page=0&size=20` | Stable Spring Data page of `{id, name, price, categoryId, categoryName}` | `gateway-service` |
| `GET /api/v1/products/{id}` | One product with the same fields, or 404 | `gateway-service`, `order-service` |

The list defaults to page 0 and size 20. Page must be at least 0; size must be 1–100. Invalid paging returns 400. Unauthorized requests return 401 and disallowed clients return 403. Error bodies contain `status`, `error`, `message`, and `path`. Actuator health, info, and prometheus paths are available without a token on the internal service network.

## Admin writes

The Gateway accepts an admin user token, validates it, and forwards a `gateway-service` token and the trusted `ADMIN` role to Product. Product allows these writes only when the service token has `azp=gateway-service` and the forwarded role is `ADMIN`. Missing tokens return 401; other clients or roles return 403.

| Endpoint | Success | Errors |
| --- | --- | --- |
| `POST /api/v1/products` | 201 `ProductView` and `Location: /api/v1/products/{id}` | 400 invalid body or unknown category |
| `PUT /api/v1/products/{id}` | 200 `ProductView` | 400 invalid body or unknown category; 404 unknown product |
| `DELETE /api/v1/products/{id}` | 204 | 404 unknown product |

Create and update accept `{"name":"Desk Lamp","price":34.00,"categoryId":1}`. Name must not be blank and must be at most 255 characters; price must be positive with at most two decimal places. Responses contain `{id, name, price, categoryId, categoryName}`, never the JPA entity. Errors use `{status, error, message, path}`.

With an admin user token in `ADMIN_TOKEN`, send a create request through the Gateway:

```bash
curl -i -X POST 'http://localhost:8080/api/v1/products' -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data '{"name":"Desk Lamp","price":34.00,"categoryId":1}'
```

## Tests

From the repository root (Docker is required for `ProductRepositoryIT`):

```powershell
mvn -B -q -pl services/product-service -am verify
mvn -B -q -pl services/product-service -am test -Dtest='!*IT'
```
