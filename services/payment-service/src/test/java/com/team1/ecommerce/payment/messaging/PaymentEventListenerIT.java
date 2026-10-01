package com.team1.ecommerce.payment.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team1.ecommerce.payment.service.ChargeSimulator;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

/** Payment's side of the Saga on real PostgreSQL and Kafka. */
@SpringBootTest(properties = {"spring.kafka.listener.auto-startup=true", "outbox.publisher.enabled=true",
        "outbox.publisher.poll-interval=200"})
@ActiveProfiles("test")
@Testcontainers
class PaymentEventListenerIT {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.9");

    @Container
    @ServiceConnection
    static final KafkaContainer kafka = new KafkaContainer("apache/kafka:3.9.1");

    /** The simulated charge is the boundary: real by default (failure rate 0.0), forced to decline in one test. */
    @MockitoSpyBean ChargeSimulator simulator;

    @Autowired JdbcTemplate jdbc;
    @Autowired KafkaTemplate<String, String> producer;
    @Autowired ObjectMapper mapper;

    @AfterEach
    void resetCharge() {
        reset(simulator);
    }

    @Test
    void inventoryReservedChargesAndPublishesPaymentCompleted() throws Exception {
        UUID orderId = UUID.randomUUID();

        inventoryReserved(UUID.randomUUID(), orderId, "49.90");

        JsonNode event = readPaymentEvents(orderId, 1).getFirst();
        assertThat(event.get("eventType").asText()).isEqualTo("PaymentCompleted");
        assertThat(event.get("amount").decimalValue()).isEqualByComparingTo("49.90");
        assertThat(paymentStatus(orderId)).isEqualTo("COMPLETED");
    }

    @Test
    void duplicateInventoryReservedChargesOnce() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        inventoryReserved(eventId, orderId, "20.00");
        inventoryReserved(eventId, orderId, "20.00");
        inventoryReserved(UUID.randomUUID(), orderId, "20.00");

        await().atMost(Duration.ofSeconds(15)).until(() -> processedCount(eventId) == 1);
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).until(() -> paymentRows(orderId) == 1);
        assertThat(readPaymentEvents(orderId, 1)).hasSize(1);
    }

    @Test
    void exhaustedRetriesPublishPaymentFailed() throws Exception {
        doReturn(false).when(simulator).charge();
        UUID orderId = UUID.randomUUID();

        inventoryReserved(UUID.randomUUID(), orderId, "15.00");

        JsonNode event = readPaymentEvents(orderId, 1).getFirst();
        assertThat(event.get("eventType").asText()).isEqualTo("PaymentFailed");
        assertThat(paymentStatus(orderId)).isEqualTo("FAILED");
        verify(simulator, times(3)).charge();
    }

    private void inventoryReserved(UUID eventId, UUID orderId, String amount) throws Exception {
        Map<String, Object> event = Map.of("eventId", eventId, "eventType", "InventoryReserved", "version", 1,
                "orderId", orderId, "totalAmount", new BigDecimal(amount));
        producer.send("inventory-events", orderId.toString(), mapper.writeValueAsString(event)).get();
    }

    private List<JsonNode> readPaymentEvents(UUID orderId, int count) throws Exception {
        List<JsonNode> events = new ArrayList<>();
        try (var consumer = new KafkaConsumer<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"), new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of("payment-events"));
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

    private String paymentStatus(UUID orderId) {
        return jdbc.queryForObject("SELECT status FROM payment WHERE order_id = ?", String.class, orderId);
    }

    private int paymentRows(UUID orderId) {
        return jdbc.queryForObject("SELECT count(*) FROM payment WHERE order_id = ?", Integer.class, orderId);
    }

    private int processedCount(UUID eventId) {
        return jdbc.queryForObject("SELECT count(*) FROM processed_event WHERE event_id = ?", Integer.class, eventId);
    }
}
