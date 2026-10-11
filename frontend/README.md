# Storefront (web UI)

A browser front end for the platform, at **http://localhost:8070**. It shows the project working end to end: browse the
catalogue, sign in through Keycloak, place an order and watch the Saga finish, write a review, and manage products and
stock as an admin. A side panel lists every API call the page makes with its status code, so 401, 403, 409 and 429
answers from the Gateway are visible during the demo.

It is plain HTML, CSS and JavaScript (`site/`), no build step. It is not a platform service and adds no business
logic: it only calls the public APIs through the Gateway, like Postman or curl would.

## How it is served

`docker compose` runs it as the `storefront` service (nginx). `nginx.conf` serves `site/` and proxies:

| Browser path | Goes to | Why |
| --- | --- | --- |
| `/api/...` | `api-gateway:8080` | every business call goes through the Gateway (JWT check, roles, rate limit) |
| `/auth/realms/...` | `keycloak:8180/realms/...` | sign-in with the `user-sign-in` client (password grant, test users) |

Because the page and both backends share one origin, no CORS rule changes on the Gateway or Keycloak. Tokens still
carry the issuer `http://localhost:8180/realms/ecommerce-platform` (Keycloak runs with `KC_HOSTNAME`), so the Gateway
accepts them.

## Run

With the platform running (`docker compose ... up -d --build --wait`), the storefront starts with it. To start only it:

```sh
docker compose -f deployment/docker/docker-compose.yml --env-file deployment/docker/.env up -d storefront
```

Sign in as `customer-test` (shop, order, review, My orders) or `admin-test` (Admin tab). The passwords are
`CUSTOMER_USER_PASSWORD` and `ADMIN_USER_PASSWORD` in `deployment/docker/.env`.

## Demo ideas

- **Saga:** place an order and watch the track on the right go from *Order saved* to *Confirmed* (about 1 s).
- **Compensation:** restart payment with `PAYMENT_FAILURE_RATE=1.0`, order again: the track ends in *Cancelled* and
  the Admin stock numbers come back.
- **Rate limit:** press *Rate-limit test*: 250 anonymous reads at once; the extra ones answer 429.
- **Security:** as a customer, the Admin tab is hidden; calling an admin API anyway answers 403 in the request list.
- **Cache:** edit a product price in Admin and reload the shop: the new price shows at once (cache evicted on write).

## Guided tour (`/tour/`)

**http://localhost:8070/tour/** is a narrated presentation of the project. The browser's built-in voice reads each step
(Web Speech API, no account or download), and the steps advance on their own.

1. **Why microservices** — the monolith problem, why it fits this shop, the costs and the fix for each, when a monolith
   would win, and our own decisions.
2. **Sessions S0–S24** — one plain-language recap per course session: the story, the idea, and where it is in our code.
3. **Live platform** — calls the running platform and shows the real answers: Config Server values, the Eureka
   registry, 200/401/403 at the Gateway, the decoded Keycloak token, cache timings, the rate limit, a real order going
   PENDING → CONFIRMED, its Zipkin trace, a review (201/409) and Prometheus targets.

Type the `customer-test` password (from `deployment/docker/.env`) in the header box. It stays in the page's memory only.
**Open tool window** opens one extra window; every tool step (Zipkin, Prometheus, Grafana, Swagger, Keycloak) loads its
real page there, so it can sit on the projector while the tour runs on the laptop. Keys: space = play/pause,
← / → = previous / next.

For the tour, `nginx.conf` also exposes four read-only (GET) views: the order-service config document, the Eureka
registry, Zipkin trace search and the Prometheus query API. Nothing else on those servers is reachable.
