# Enterprise E-Commerce Platform

Team 1's Java 21 / Spring Boot 3.5.16 / Spring Cloud 2025.0.3 monorepo. L0 provides eight independent applications and shared local infrastructure. Business APIs begin in L1.

## Prerequisites

Java 21 or newer, Maven, Docker Compose, Bash, curl, and jq (for the token example below). Run all Maven commands from the repository root. Config Server's default native path supports `mvn -pl platform/config-server spring-boot:run`; set `CONFIG_REPO_LOCATION` to a file URI for a different working directory.

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

## Gateway and Keycloak

Start Config Server, then Eureka as above. Set `KEYCLOAK_ISSUER_URI` to the externally reachable realm URL (default `http://localhost:8180/realms/ecommerce-platform`) and `GATEWAY_CLIENT_SECRET` to the `gateway-service` secret in `deployment/docker/.env`. Start the Gateway from the repository root:

```sh
export KEYCLOAK_ISSUER_URI=http://localhost:8180/realms/ecommerce-platform
export GATEWAY_CLIENT_SECRET='your-local-gateway-client-secret'
mvn -pl platform/api-gateway spring-boot:run
```

The Gateway validates `user-sign-in` tokens and obtains its own client credentials token for downstream requests. To get a customer token and verify that a protected admin route rejects it with `403`, use the local test user's password from `deployment/docker/.env` in another shell:

```sh
export CUSTOMER_USER_PASSWORD='your-local-customer-test-password'
TOKEN="$(curl --fail-with-body -sS -X POST 'http://localhost:8180/realms/ecommerce-platform/protocol/openid-connect/token' \
  --data-urlencode 'grant_type=password' \
  --data-urlencode 'client_id=user-sign-in' \
  --data-urlencode 'username=customer-test' \
  --data-urlencode "password=$CUSTOMER_USER_PASSWORD" | jq -er '.access_token')"
curl -i -X POST -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/products
```

After changing `deployment/docker/keycloak/realm-export.json`, recreate the local Keycloak container and its data volume. Keycloak imports the realm only on first start:

```sh
docker compose -f deployment/docker/docker-compose.yml --env-file deployment/docker/.env rm -sf keycloak
docker volume rm docker_keycloak_data
docker compose -f deployment/docker/docker-compose.yml --env-file deployment/docker/.env up -d keycloak
```
