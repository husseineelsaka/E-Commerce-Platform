# Enterprise E-Commerce Platform

Team 1's Java 21 / Spring Boot 3.5.16 / Spring Cloud 2025.0.3 monorepo. L0 provides eight independent applications and shared local infrastructure. Business APIs begin in L1.

## Prerequisites

Java 21 or newer, Maven, Docker Compose, Bash, and curl. Run all Maven commands from the repository root. Config Server's default native path supports `mvn -pl platform/config-server spring-boot:run`; set `CONFIG_REPO_LOCATION` to a file URI for a different working directory.

## Start L0

```sh
cp deployment/docker/.env.example deployment/docker/.env
# Edit deployment/docker/.env and replace every CHANGE_ME value.
docker compose -f deployment/docker/docker-compose.yml --env-file deployment/docker/.env up -d
mvn -pl platform/config-server spring-boot:run
```

In another terminal after Config Server is healthy:

```sh
mvn -pl platform/eureka-server spring-boot:run
```

In a third terminal:

```sh
bash scripts/verify-l0.sh
```

Build all modules with `mvn -B verify`. Each module can run with `mvn -pl <module-path> spring-boot:run`. App Dockerfiles use the repository root as build context, for example `docker build -f platform/config-server/Dockerfile .`.

See [L0 phase](docs/phases/L0.md) for decisions and gate status.
