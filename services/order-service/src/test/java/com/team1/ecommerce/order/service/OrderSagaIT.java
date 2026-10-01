package com.team1.ecommerce.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
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

/** Order's side of the Saga on real PostgreSQL and Kafka: outbox publishing, outcomes, duplicates, late events. */
@SpringBootTest(properties = {"spring.kafka.listener.auto-startup=true", "outbox.publisher.enabled=true",
        "outbox.publisher.poll-interval=200"})
@ActiveProfiles("test")
@Testcontainers
class OrderSagaIT {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.9");

    @Container
    @ServiceConnection
    static final KafkaContainer kafka = new KafkaContainer("apache/kafka:3.9.1");

    @Autowired OrderWriter writer;
    @Autowired JdbcTemplate jdbc;
    @Autowired KafkaTemplate<String, String> producer;
    @Autowired ObjectMapper mapper;

    @Test
    void outboxPublisherSendsOrderPlacedAndMarksItPublished() throws Exception {
        UUID orderId = placeOrder();

        List<JsonNode> events = readOrderEvents(orderId, 1);

        assertThat(events.getFirst().get("eventType").asText()).isEqualTo("OrderPlaced");
        assertThat(events.getFirst().get("totalAmount").decimalValue()).isEqualByComparingTo("25.00");
        await().atMost(Duration.ofSeconds(10)).until(() -> unpublished(orderId) == 0);
    }

    @Test
    void paymentCompletedConfirmsOrderAndPublishesOrderConfirmed() throws Exception {
        UUID orderId = placeOrder();

        send("payment-events", orderId, "PaymentCompleted", UUID.randomUUID(), Map.of("amount", 25.00));

        await().atMost(Duration.ofSeconds(15)).until(() -> "CONFIRMED".equals(status(orderId)));
        assertThat(readOrderEvents(orderId, 2)).extracting(event -> event.get("eventType").asText())
                .containsExactly("OrderPlaced", "OrderConfirmed");
    }

    @Test
    void paymentFailedCancelsOrderAndPublishesOrderCancelled() throws Exception {
        UUID orderId = placeOrder();

        send("payment-events", orderId, "PaymentFailed", UUID.randomUUID(), Map.of("reason", "Card declined"));

        await().atMost(Duration.ofSeconds(15)).until(() -> "CANCELLED".equals(status(orderId)));
        JsonNode cancelled = readOrderEvents(orderId, 2).get(1);
        assertThat(cancelled.get("eventType").asText()).isEqualTo("OrderCancelled");
        assertThat(cancelled.get("reason").asText()).isEqualTo("Card declined");
    }

    @Test
    void reservationFailedCancelsOrder() throws Exception {
        UUID orderId = placeOrder();

        send("inventory-events", orderId, "InventoryReservationFailed", UUID.randomUUID(), Map.of("reason", "Out of stock"));

        await().atMost(Duration.ofSeconds(15)).until(() -> "CANCELLED".equals(status(orderId)));
    }

    @Test
    void lateEventIsIgnored() throws Exception {
        UUID orderId = placeOrder();
        send("payment-events", orderId, "PaymentCompleted", UUID.randomUUID(), Map.of("amount", 25.00));
        await().atMost(Duration.ofSeconds(15)).until(() -> "CONFIRMED".equals(status(orderId)));

        UUID late = UUID.randomUUID();
        send("payment-events", orderId, "PaymentFailed", late, Map.of("reason", "late"));
        await().atMost(Duration.ofSeconds(15)).until(() -> processed(late));

        assertThat(status(orderId)).isEqualTo("CONFIRMED");
        assertThat(outboxRows(orderId)).isEqualTo(2);
    }

    @Test
    void duplicateEventIsProcessedOnce() throws Exception {
        UUID orderId = placeOrder();
        UUID eventId = UUID.randomUUID();

        send("payment-events", orderId, "PaymentCompleted", eventId, Map.of("amount", 25.00));
        send("payment-events", orderId, "PaymentCompleted", eventId, Map.of("amount", 25.00));
        await().atMost(Duration.ofSeconds(15)).until(() -> "CONFIRMED".equals(status(orderId)));

        assertThat(outboxRows(orderId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_event WHERE event_id = ?", Integer.class, eventId))
                .isEqualTo(1);
    }

    private UUID placeOrder() {
        return writer.save("customer-1", List.of(new OrderWriter.PricedItem(1L, 2, new BigDecimal("12.50"))));
    }

    private void send(String topic, UUID orderId, String type, UUID eventId, Map<String, Object> fields) throws Exception {
        var event = new java.util.LinkedHashMap<String, Object>(Map.of("eventId", eventId, "eventType", type,
                "version", 1, "orderId", orderId));
        event.putAll(fields);
        producer.send(topic, orderId.toString(), mapper.writeValueAsString(event)).get();
    }

    /** Reads this order's events from order-events with a fresh consumer group, waiting until {@code count} arrived. */
    private List<JsonNode> readOrderEvents(UUID orderId, int count) throws Exception {
        List<JsonNode> events = new ArrayList<>();
        try (var consumer = new KafkaConsumer<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"), new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of("order-events"));
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

    private String status(UUID orderId) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    private int unpublished(UUID orderId) {
        return jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE aggregate_id = ? AND published_at IS NULL",
                Integer.class, orderId);
    }

    private int outboxRows(UUID orderId) {
        return jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE aggregate_id = ?", Integer.class, orderId);
    }

    private boolean processed(UUID eventId) {
        return jdbc.queryForObject("SELECT count(*) FROM processed_event WHERE event_id = ?", Integer.class, eventId) == 1;
    }
}
