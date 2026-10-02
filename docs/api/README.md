# API documentation

| File | What it is |
| --- | --- |
| `openapi.yaml` | OpenAPI 3 description of every endpoint: the Gateway routes, plus the internal service-to-service endpoints (tag `internal`) |
| `postman/E-Commerce-Platform.postman_collection.json` | 46 requests with assertions, in run order: tokens, products, reviews, inventory, orders, internal APIs, cleanup |
| `postman/local.postman_environment.json` | URLs for Docker Compose and empty secret fields; the secrets stay in your local `deployment/docker/.env` |

## Swagger UI

Docker Compose starts Swagger UI with the platform:

```sh
docker compose -f deployment/docker/docker-compose.yml --env-file deployment/docker/.env up -d swagger-ui
```

Open `http://localhost:8089`. "Try it out" calls the Gateway on `http://localhost:8080`; the Gateway allows this one browser
origin (`gateway.cors.allowed-origins` in `config-repo/api-gateway.yml`). For protected endpoints, get a token as described
at the top of the page, click **Authorize**, and paste it. The `internal` endpoints are listed for completeness; a browser
cannot reach them.

## Postman

1. Import both files from `postman/`.
2. Select the environment **Local (Docker Compose)** and fill in `customerPassword`, `adminPassword`, `orderClientSecret`,
   and `paymentOperatorSecret` from `deployment/docker/.env` (`CUSTOMER_USER_PASSWORD`, `ADMIN_USER_PASSWORD`,
   `ORDER_CLIENT_SECRET`, `PAYMENT_OPERATOR_CLIENT_SECRET`). Postman keeps them in your local environment only.
3. Run folder **0 Auth** once (tokens are valid for 5 minutes), then any request or the whole collection.

Each run creates its own product for the review tests and deletes it at the end, so it can run again. Folder
**5 Internal** needs the Compose network: from the host those requests cannot connect, because payment-service and
inventory-service publish no port (ADD §7).

## Run everything with Newman

Inside the Compose network every folder works, including the internal one:

```sh
v() { grep "^$1=" deployment/docker/.env | cut -d= -f2-; }
docker run --rm --network docker_default -v "$PWD/docs/api/postman:/etc/newman" postman/newman:6-alpine \
  run E-Commerce-Platform.postman_collection.json -e local.postman_environment.json \
  --env-var baseUrl=http://api-gateway:8080 --env-var keycloakUrl=http://keycloak:8180 \
  --env-var "customerPassword=$(v CUSTOMER_USER_PASSWORD)" --env-var "adminPassword=$(v ADMIN_USER_PASSWORD)" \
  --env-var "orderClientSecret=$(v ORDER_CLIENT_SECRET)" --env-var "paymentOperatorSecret=$(v PAYMENT_OPERATOR_CLIENT_SECRET)" \
  --delay-request 50
```

Recorded 2026-10-02 on Docker Compose: 46 requests, 62 assertions, 0 failures (two consecutive runs, 10.3 s each).
The two "after the event" requests wait 3 s before they run, because the order status and the product rating are
updated through Kafka.
