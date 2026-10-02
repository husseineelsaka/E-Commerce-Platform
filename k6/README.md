# k6

| Script | What it measures | Target (ADD §8, NFR-02/03) |
| --- | --- | --- |
| `smoke-test.js` | 1 VU, every public and customer endpoint once per second for 30 s | 0 errors |
| `load-products.js` | anonymous product reads at 60 req/s for 2 min | P95 < 200 ms, ≥ 50 req/s, no 429 |
| `load-orders.js` | 20 VUs placing orders with `sleep(1)` for 2 min | order POST P95 < 800 ms, no 429 |
| `stress-products.js` | product reads in five one-minute stages up to `PEAK` req/s (default 2000), tagged per stage | find the breaking point |
| `constant-products.js` | a fixed `RATE` req/s (default 800) for `DURATION` (default 120 s) | before/after comparison in the Performance Report |

Run them with the k6 container on the Compose network (the platform must be up):

```sh
docker run --rm -i --network docker_default -v "$PWD/k6:/scripts" \
  -e BASE_URL=http://api-gateway:8080 -e KEYCLOAK_URL=http://keycloak:8180 -e CUSTOMER_PASSWORD="$CUSTOMER_USER_PASSWORD" \
  grafana/k6:1.3.0 run /scripts/load-orders.js
```

For `stress-products.js`, start the Gateway with the anonymous limit raised so the run measures the platform, not the limiter:

```sh
GATEWAY_ANON_REPLENISH_RATE=5000 GATEWAY_ANON_BURST=10000 \
  docker compose -f deployment/docker/docker-compose.yml --env-file deployment/docker/.env up -d config-server api-gateway
```

Results and the bottleneck analysis are in `docs/PERFORMANCE-REPORT.md`.
