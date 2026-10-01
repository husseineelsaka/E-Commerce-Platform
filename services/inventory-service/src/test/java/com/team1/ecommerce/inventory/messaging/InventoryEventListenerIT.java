package com.team1.ecommerce.inventory.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

/** Inventory's side of the Saga on real PostgreSQL and Kafka: reserve, fail, release, and duplicate delivery. */
@SpringBootTest(properties = {"spring.kafka.listener.auto-startup=true", "outbox.publisher.enabled=true",
        "outbox.publisher.poll-interval=200"})
@ActiveProfiles("test")
@Testcontainers
class InventoryEventListenerIT {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.9");

    @Container
    @ServiceConnection
    static final KafkaContainer kafka = new KafkaContainer("apache/kafka:3.9.1");

    @Autowired JdbcTemplate jdbc;
    @Autowired KafkaTemplate<String, String> producer;
    @Autowired ObjectMapper mapper;

    @BeforeEach
    void stock() {
        jdbc.update("UPDATE stock SET available = 10, reserved = 0 WHERE product_id IN (1, 2)");
    }

    @Test
    void orderPlacedReservesStockAndPublishesInventoryReserved() throws Exception {
        UUID orderId = UUID.randomUUID();

        orderPlaced(UUID.randomUUID(), orderId, 1L, 3);

        await().atMost(Duration.ofSeconds(15)).until(() -> reserved(orderId) == 3);
        assertThat(available(1L)).isEqualTo(7);
        assertThat(stockReserved(1L)).isEqualTo(3);
        JsonNode event = readInventoryEvents(orderId, 1).getFirst();
        assertThat(event.get("eventType").asText()).isEqualTo("InventoryReserved");
        assertThat(event.get("totalAmount").decimalValue()).isEqualByComparingTo("37.50");
    }

    @Test
    void duplicateOrderPlacedReservesOnce() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        orderPlaced(eventId, orderId, 2L, 4);
        orderPlaced(eventId, orderId, 2L, 4);

        await().atMost(Duration.ofSeconds(15)).until(() -> processed(eventId));
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).until(() -> available(2L) == 6);
        assertThat(stockReserved(2L)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE aggregate_id = ?", Integer.class, orderId))
                .isEqualTo(1);
    }

    @Test
    void insufficientStockPublishesReservationFailedAndChangesNothing() throws Exception {
        UUID orderId = UUID.randomUUID();

        orderPlaced(UUID.randomUUID(), orderId, 1L, 11);

        JsonNode event = readInventoryEvents(orderId, 1).getFirst();
        assertThat(event.get("eventType").asText()).isEqualTo("InventoryReservationFailed");
        assertThat(available(1L)).isEqualTo(10);
        assertThat(reserved(orderId)).isZero();
    }

    @Test
    void paymentFailedReleasesTheReservation() throws Exception {
        UUID orderId = UUID.randomUUID();
        orderPlaced(UUID.randomUUID(), orderId, 1L, 2);
        await().atMost(Duration.ofSeconds(15)).until(() -> reserved(orderId) == 2);

        send("payment-events", orderId, Map.of("eventId", UUID.randomUUID(), "eventType", "PaymentFailed",
                "reason", "Card declined"));

        // NFR-05: no RESERVED row is left for a failed order.
        await().atMost(Duration.ofSeconds(15)).until(() -> reserved(orderId) == 0);
        assertThat(available(1L)).isEqualTo(10);
        assertThat(stockReserved(1L)).isZero();
        assertThat(readInventoryEvents(orderId, 2)).extracting(event -> event.get("eventType").asText())
                .containsExactly("InventoryReserved", "InventoryReleased");
    }

    private void orderPlaced(UUID eventId, UUID orderId, long productId, int quantity) throws Exception {
        send("order-events", orderId, Map.of("eventId", eventId, "eventType", "OrderPlaced", "customerId", "customer-1",
                "items", List.of(Map.of("productId", productId, "quantity", quantity)), "totalAmount", 12.50 * quantity));
    }

    private void send(String topic, UUID orderId, Map<String, Object> fields) throws Exception {
        Map<String, Object> event = new LinkedHashMap<>(fields);
        event.put("version", 1);
        event.put("orderId", orderId);
        producer.send(topic, orderId.toString(), mapper.writeValueAsString(event)).get();
    }

    private List<JsonNode> readInventoryEvents(UUID orderId, int count) throws Exception {
        List<JsonNode> events = new ArrayList<>();
        try (var consumer = new KafkaConsumer<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"), new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of("inventory-events"));
            long deadline = System.currentTimeMillis() + 15_000;
            while (events.size() < count && System.currentTimeMillis() < deadline) {
                for (var record : consumer.poll(Duration.ofMillis(500))) {
                    if (record.key().equals(orderId.toString())) {
                        events.add(mapper.readTree(record.value()));
                    }
                }
            }
        }
        assertThat(events).hasSize(count);
        return events;
    }

    private int available(long productId) {
        return jdbc.queryForObject("SELECT available FROM stock WHERE product_id = ?", Integer.class, productId);
    }

    private int stockReserved(long productId) {
        return jdbc.queryForObject("SELECT reserved FROM stock WHERE product_id = ?", Integer.class, productId);
    }

    private int reserved(UUID orderId) {
        return jdbc.queryForObject("SELECT coalesce(sum(quantity), 0) FROM reservation WHERE order_id = ? AND status = 'RESERVED'",
                Integer.class, orderId);
    }

    private boolean processed(UUID eventId) {
        return jdbc.queryForObject("SELECT count(*) FROM processed_event WHERE event_id = ?", Integer.class, eventId) == 1;
    }
}
