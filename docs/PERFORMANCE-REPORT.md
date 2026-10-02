# Performance Report — L5

Team 1 · Enterprise E-Commerce Platform · recorded 2026-10-02

## Summary

| Target (ADD §8) | Result | Status |
| --- | --- | --- |
| NFR-02: cached product GET P95 < 200 ms | 8.1 ms at 75 req/s | Met |
| NFR-02: `POST /api/v1/orders` P95 < 800 ms at 20 VUs | 61.6 ms (209 ms before the fix) | Met |
| NFR-03: Gateway read throughput ≥ 50 req/s | 75 req/s below the anonymous limit; 800 req/s with the limit raised, P95 18.8 ms | Met |
| Smoke: 0 errors | 108/108 checks | Met |
| One bottleneck found and fixed | Gateway CPU per request −27 %; P95 at 800 req/s 518 ms → 18.8 ms | Done |

**Bottleneck:** the API Gateway. At 800 req/s it added about 430 ms to the P95 while product-service answered in 28 ms. A CPU profile showed that most of the extra work came from observation, not routing. Spring Security created 9 of the 12 spans of every anonymous product read. The rate limiter also re-read its Lua script's timestamp from the jar on every request. We removed both costs.

## Environment

- **Host:** Docker Desktop on Windows 11; the Linux VM has 12 vCPUs and 7.6 GB RAM.
- **Platform:** all 16 Compose containers on one host, with tracing sampled at 100 % into Zipkin. The kind cluster was stopped during the runs.
- **Load generator:** k6 1.3.0 in a container on the Compose network. It targets `http://api-gateway:8080`, so every request passes the Gateway, Spring Security, the Redis rate limiter, and Eureka load balancing.
- **Warm-up:** each latency run starts after warm-up requests. Results come from k6 summaries and Prometheus (`http_server_requests` histograms, `process_cpu_time_ns_total`).

## Scripts

| Script | Load |
| --- | --- |
| `k6/smoke-test.js` | 1 VU for 30 s: list, detail, place order, own orders |
| `k6/load-products.js` | 60 iterations/s of anonymous reads for 2 min (about 75 req/s; stays below the anonymous limit of 100 req/s) |
| `k6/load-orders.js` | 20 VUs placing orders with `sleep(1)` for 2 min |
| `k6/stress-products.js` | anonymous reads in five 1-minute stages: 100, 200, 400, 600, 800 req/s (`PEAK=800`), tagged per stage |
| `k6/constant-products.js` | a fixed 800 req/s for 120 s, used for the A/B runs |

The stress and constant-rate runs raise the anonymous limit (`GATEWAY_ANON_REPLENISH_RATE=5000`, `GATEWAY_ANON_BURST=10000`) so they measure the platform, not the limiter. Smoke and load runs use the ADD §7 defaults.

## Finding the bottleneck

1. **Stress ramp.** P95 stayed under 120 ms up to 600 req/s, then jumped to 518 ms at 800 req/s, with 1,494 dropped iterations.
2. **Which hop.** Server-side P95 from Prometheus during the 800 req/s stage: Gateway **458 ms**, product-service **28 ms**. The time was spent in the Gateway.
3. **Not Redis, GC, or the database.**
   - Redis: rate-limiter `EVALSHA` averaged 6 ms.
   - GC: pauses took 14 ms per second.
   - Database: Postgres stayed near 1 % CPU, because product reads come from the Redis cache.
   - CPU: the Gateway was using about 3.5 cores.
4. **CPU profile.** A JFR recording of the Gateway at 800 req/s (4,188 samples):
   - 22 % of stacks were in Spring Security's observation wrappers (`ObservationWebFilterChainDecorator`, `AuthorizationObservationConvention`).
   - 21 % were in Micrometer observation handling, and 7 % in Brave span handling.
   - 5.2 % were jar-file lookups from `DefaultRedisScript.getSha1` → `ResourceScriptSource.isModified`. Spring Cloud Gateway's auto-configured rate-limiter script checks the Lua file's last-modified time inside the jar before every `EVALSHA`.
5. **Zipkin confirmed it.** One anonymous product read produced 12 spans, and 9 of them were Spring Security's: filter chain before/after, authorize, authenticate, and secured request, in both the Gateway and product-service.

## Fix

| Change | Where |
| --- | --- |
| `management.observations.enable.spring.security: false`. Requests, Feign calls, and Kafka messages stay traced; only the per-filter security observations go. | `config-repo/application.yml` (all services) |
| The rate limiter is built with the token-bucket script read once into a static source, so its SHA is computed once. | `platform/api-gateway/.../ratelimit/RateLimitConfig.java` |

The Gateway tests still pass: `GatewaySecurityTest` 8/8 and `RateLimitIT` 3/3.

## Before and after

### Stress ramp (same script, same warm-up)

| Stage | P95 before | P95 after | Max before | Max after | Errors |
| --- | --- | --- | --- | --- | --- |
| 100 req/s | 22.7 ms | 4.6 ms | 129 ms | 13 ms | 0 % |
| 200 req/s | 11.2 ms | 4.2 ms | 82 ms | 26 ms | 0 % |
| 400 req/s | 84.0 ms | 6.3 ms | 740 ms | 55 ms | 0 % |
| 600 req/s | 117.8 ms | 8.8 ms | 346 ms | 210 ms | 0 % |
| 800 req/s | **518.2 ms** | **18.8 ms** | 1,019 ms | 235 ms | 0 % |

Dropped iterations went from 1,494 to 0.

### CPU per request at a fixed 800 req/s

This is the most stable measure on a shared laptop, because it does not depend on what else is running.

| Build | Gateway CPU ms/request | product-service CPU ms/request |
| --- | --- | --- |
| Before | 4.23 | 2.69 |
| Script fix only | 3.77 / 3.76 | 2.75 / 2.59 |
| Script fix + no security observations | 3.08 / 3.08 / 2.81 | 2.20 / 1.96 / 1.68 |

The Gateway needs about **27 % less CPU per request**, and product-service about **25 % less**. Because the Gateway is CPU-bound, that means about 37 % more requests per core.

### Latency at a fixed 800 req/s

| Build | P95 per run (120 s each) |
| --- | --- |
| Before | 395 ms, 722 ms, 724 ms |
| Script fix only | 676 ms, 631 ms |
| Both fixes | 795 ms, 193 ms, 377 ms |

**Limitation:** these runs are noisy. Each one starts right after the Gateway container is recreated, so JIT compilation competes with 800 req/s on a laptop that also runs k6 and 15 other containers. The ramp above, which reaches 800 req/s after four minutes of lower load, and the CPU-per-request figures are the reliable comparisons.

### Other effects

| Measure | Before | After |
| --- | --- | --- |
| Spans per anonymous product read | 12 | 3 |
| Spans per order trace (HTTP + Kafka, 6 services) | 42 | 23 |
| `POST /api/v1/orders` P95 at 20 VUs | 209 ms | 61.6 ms |
| `GET /api/v1/products/{id}` P95 at 75 req/s | 6.8 ms | 8.1 ms (no change: far below saturation) |

## Other findings

- **Zipkin ran out of memory under load (fixed in #29).** The `openzipkin/zipkin` image defaults to a 160 MB heap with 500,000 in-memory spans, and it crashed with `OutOfMemoryError` during sustained load. The services logged dropped spans (`Dropped 723 spans due to HttpTimeoutException`). Compose now caps it at 100,000 spans with a 768 MB heap. With a third of the spans per request, it also receives far less data.
- **Docker VM memory is the next limit.** Compose sets no memory limits, so each application JVM may grow its heap to 25 % of the VM (1.95 GB). Ramping toward 2,000 req/s, and running the kind cluster alongside Compose, pushed the 7.6 GB VM into swap until the Docker engine stopped responding. The Helm charts already limit each pod to 768 Mi. **Next step:** the same limits in Compose (`mem_limit` with `-XX:MaxRAMPercentage`).
- **First load run was an outlier.** The very first `load-products` run (P95 479 ms) could not be reproduced: four controlled reruns (warm, two services restarted, all services restarted, Zipkin crashing) gave 7–14 ms. We treat it as a one-off disturbance on the host and do not count it as a result.

## What would make us revisit

- **Production sampling.** Lower `TRACING_SAMPLING_PROBABILITY` (for example 0.1): 100 % sampling is for the demo.
- **Gateway CPU.** If the Gateway still saturates first, the next candidates are the Gateway's own HTTP client observation and running more Gateway replicas behind the NodePort.
