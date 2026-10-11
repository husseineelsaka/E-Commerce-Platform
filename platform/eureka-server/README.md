# eureka-server

Service discovery. Port 8761. Layer L0.

## Why it exists

Services must call each other by name, not by IP and port: containers and pods get new addresses on every restart,
and a service can run as several instances. Eureka is the phone book. Every application registers its name and
address, and callers ask "where is `inventory-service`?" instead of hard-coding `http://10.0.0.7:8084`.

## How it works

- Runs `@EnableEurekaServer`. It does not register with itself (`register-with-eureka: false`).
- Each client registers on startup under `spring.application.name` (shown upper-case, for example `ORDER-SERVICE`)
  and sends a heartbeat every 30 s. A new instance can take up to about 30 s to appear, which is expected.
- The Gateway routes with `lb://order-service`; OpenFeign clients in order-service use `@FeignClient(name = "...")`.
  Spring Cloud LoadBalancer picks an instance from the Eureka registry (client-side load balancing).
- In Compose, `EUREKA_INSTANCE_PREFER_IP_ADDRESS=true` makes instances register their container IP.
- Reads its own settings from Config Server, so start Config Server first.

## Run

```sh
mvn -pl platform/eureka-server spring-boot:run          # after Config Server is UP
# browse http://localhost:8761 for the dashboard of registered instances
curl -s -H 'Accept: application/json' http://localhost:8761/eureka/apps
```

In Docker Compose it has no host port:

```sh
docker compose -f deployment/docker/docker-compose.yml exec eureka-server curl -s -H 'Accept: application/json' http://localhost:8761/eureka/apps
```

## Tests

No unit tests of its own. `scripts/verify-l0.sh` checks it is UP; `scripts/e2e-test.sh` (section 0) checks that the
Gateway and all six business services are registered.
