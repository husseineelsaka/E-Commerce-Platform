# notification-service

Notifies customers about order outcomes. Port 8085. No database (handbook). Story S12 (FR-11, NFR-10).

## Prerequisites

Kafka from `deployment/docker` running, Config Server, and Eureka. `KAFKA_BOOTSTRAP_SERVERS` defaults to `localhost:9092`.

## Run

```sh
mvn -pl services/notification-service spring-boot:run
```

## Behaviour

- Consumes `order-events` and reacts only to `OrderConfirmed` (confirmation) and `OrderCancelled` (cancel notice with the reason). Other events, and raw payment results, are ignored.
- Notifications are log lines (`NOTIFICATION to customer …`), which the handbook allows; a real e-mail or SMS gateway would replace `NotificationSender`.
- A failing send is retried with `@RetryableTopic`: 4 attempts in total, 1 s then doubling, on `order-events-retry`. After the last attempt the message is parked on **`order-events.DLT`** (the DLT suffix is set to `.DLT`; the Spring Kafka default is `-dlt`, ADD §6 F5) and an `ALERT` line is logged. Messages on the DLT can be replayed.
- Demo switch: `NOTIFICATION_FAIL=true` makes every send fail, so retries and the DLT can be shown live:

```sh
docker compose -f deployment/docker/docker-compose.yml exec kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic order-events.DLT --from-beginning
```

- No database means no deduplication: if Order's outbox re-sends the same event after a crash, the customer can get the notice twice (ADD §6 F7 accepts this for notifications).

## Tests

```sh
mvn -pl services/notification-service -am verify
```

`NotificationListenerIT` (Testcontainers Kafka, Docker required): confirmation sent once, cancel notice with reason, other events ignored, and `exhaustedRetriesGoToDlt` (4 attempts, then `order-events.DLT`).
