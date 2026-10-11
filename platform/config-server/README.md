# config-server

Central configuration for every application. Port 8888. Layer L0.

## Why it exists

Nine applications share settings: the Eureka address, Zipkin, tracing, Kafka observation, Actuator exposure. Without a
central place, each change means editing nine `application.yml` files and rebuilding nine images. Config Server serves
the files in `config-repo/` over HTTP, so every application reads the same settings at startup (12-Factor, factor III).

## How it works

- Runs Spring Cloud Config Server with the `native` profile: it reads files from a folder, not from a Git remote.
  The folder is `CONFIG_REPO_LOCATION` (default `file:../../config-repo/`, relative to this module; the Docker image
  copies `config-repo/` to `/app/config-repo/` and sets the variable).
- `config-repo/application.yml` is shared by all applications; `config-repo/<spring.application.name>.yml` is per
  application (for example `order-service.yml`).
- Each client imports it with `spring.config.import: optional:configserver:${CONFIG_SERVER_URL:http://localhost:8888}`.
  `optional:` lets a service still start from its local `application.yml` if Config Server is down.
- It does not register in Eureka (`fetch-registry: false`): it must start first, before Eureka exists.
- Secrets are never in `config-repo/`; files hold `${ENV_VAR}` placeholders filled from the environment.

## Run

Start it first, from the repository root:

```sh
mvn -pl platform/config-server spring-boot:run
curl http://localhost:8888/actuator/health                 # {"status":"UP"}
curl http://localhost:8888/order-service/default           # the merged configuration Order will receive
```

In Docker Compose it starts automatically and has no host port; check it from another container:

```sh
docker compose -f deployment/docker/docker-compose.yml exec eureka-server curl -s http://config-server:8888/application/default
```

## Tests

No unit tests of its own. `scripts/verify-l0.sh` and `scripts/e2e-test.sh` (section 0) check that it serves
`application/default`.
