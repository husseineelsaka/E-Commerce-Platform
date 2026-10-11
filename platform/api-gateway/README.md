# api-gateway

The single entry point and the user trust boundary. Port 8080 (API), management port 8090 (Actuator). Layer L1.
Spring Cloud Gateway on WebFlux (Netty), not Spring MVC.

## Why it exists

Customers and admins must see one address, and security must be enforced in one place. Without a Gateway every
service would be public, would validate user tokens itself, and would need its own rate limiting. The Gateway is the
only published port in Compose and Kubernetes; business services stay internal.

## What it does, in request order

1. **Rate limiting (FR-13)** — every route has `RequestRateLimiter` with a Redis token bucket
   (`RateLimitConfig`). Signed-in clients are keyed by JWT subject (20 req/s, burst 40); anonymous clients by IP
   (100 req/s, burst 200). `X-Forwarded-For` is trusted only from `gateway.rate-limit.trusted-proxies`. An empty bucket
   answers `429`. If Redis is down it fails open (requests pass, limits not enforced).
2. **Authentication (FR-04)** — OAuth2 Resource Server (`GatewaySecurityConfig`, `@EnableWebFluxSecurity`). It validates
   the Keycloak JWT: signature from the JWKS, issuer, expiry, and `azp = user-sign-in`. No or bad token on a protected
   route → `401`.
3. **Authorization by route** — public: `GET /api/v1/products/**` (catalogue and review reads). `ADMIN`: product
   writes, `GET/PUT /api/v1/inventory/{productId}` (also `low-stock`). `CUSTOMER`: `/api/v1/orders/**` and review
   submit. `/api/v1/inventory/check` and payments are never routed. Everything else is denied. Wrong role → `403`.
4. **Identity hand-off** — `TrustedIdentityFilter` removes every client-sent `X-User-*` header, adds `X-User-Id`
   (the JWT `sub`) and `X-User-Roles` from the validated token, and replaces the bearer token with the Gateway's own
   `gateway-service` Client Credentials token. Services trust identity headers only when `azp = gateway-service`.
5. **Routing** — `lb://<service>` through Eureka. The `reviews` route is declared before `product-reads`, otherwise
   `/api/v1/products/{id}/reviews` would reach product-service.

## Run

Needs Config Server, Eureka, Keycloak and Redis running.

```sh
export KEYCLOAK_ISSUER_URI=http://localhost:8180/realms/ecommerce-platform
export GATEWAY_CLIENT_SECRET='<gateway-service secret from deployment/docker/.env>'
mvn -pl platform/api-gateway spring-boot:run
curl -i http://localhost:8080/api/v1/products          # 200, public
curl -i -X POST http://localhost:8080/api/v1/products  # 401, no token
```

Rates are in `config-repo/api-gateway.yml` (`GATEWAY_ANON_REPLENISH_RATE`, `GATEWAY_ANON_BURST`, ...).

## Tests

```sh
mvn -pl platform/api-gateway -am verify
```

`GatewaySecurityTest` (header stripping, 401/403 rules, route ordering for reviews, internal routes not forwarded, CORS)
and `RateLimitIT` (Testcontainers Redis: one client limited while another is served, forged `X-Forwarded-For` ignored).
