package com.team1.ecommerce.notification.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team1.ecommerce.notification.service.NotificationSender;
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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

/** Notifications on real Kafka: confirmation, cancel notice, ignored events, retries, and the DLT. */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class NotificationListenerIT {
    @Container
    @ServiceConnection
    static final KafkaContainer kafka = new KafkaContainer("apache/kafka:3.9.1");

    /** The notification gateway is the boundary: real (log) by default, forced to fail in one test. */
    @MockitoSpyBean NotificationSender sender;

    @Autowired KafkaTemplate<String, String> producer;
    @Autowired ObjectMapper mapper;

    @AfterEach
    void resetSender() {
        reset(sender);
    }

    @Test
    void orderConfirmedSendsOneConfirmation() throws Exception {
        String orderId = UUID.randomUUID().toString();

        publish(orderId, Map.of("eventType", "OrderConfirmed", "customerId", "customer-1"));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                verify(sender, times(1)).send(eq("customer-1"), contains("is confirmed")));
    }

    @Test
    void orderCancelledSendsCancelNoticeWithReason() throws Exception {
        String orderId = UUID.randomUUID().toString();

        publish(orderId, Map.of("eventType", "OrderCancelled", "customerId", "customer-2", "reason", "Payment declined"));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                verify(sender).send(eq("customer-2"), contains("cancelled: Payment declined")));
    }

    @Test
    void otherOrderEventsSendNothing() throws Exception {
        String orderId = UUID.randomUUID().toString();

        publish(orderId, Map.of("eventType", "OrderPlaced", "customerId", "customer-3"));

        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                verify(sender, never()).send(eq("customer-3"), anyString()));
    }

    @Test
    void exhaustedRetriesGoToDlt() throws Exception {
        doThrow(new IllegalStateException("gateway down")).when(sender).send(eq("customer-dlt"), anyString());
        String orderId = UUID.randomUUID().toString();

        publish(orderId, Map.of("eventType", "OrderConfirmed", "customerId", "customer-dlt"));

        assertThat(readDlt(orderId)).contains("customer-dlt");
        verify(sender, times(4)).send(eq("customer-dlt"), anyString());
    }

    private void publish(String orderId, Map<String, Object> fields) throws Exception {
        var event = new java.util.LinkedHashMap<String, Object>(fields);
        event.put("eventId", UUID.randomUUID());
        event.put("version", 1);
        event.put("orderId", orderId);
        producer.send("order-events", orderId, mapper.writeValueAsString(event)).get();
    }

    private String readDlt(String orderId) {
        List<String> found = new ArrayList<>();
        try (var consumer = new KafkaConsumer<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"), new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of("order-events.DLT"));
            long deadline = System.currentTimeMillis() + 30_000;
            while (found.isEmpty() && System.currentTimeMillis() < deadline) {
                consumer.poll(Duration.ofMillis(500)).forEach(record -> {
                    if (orderId.equals(record.key())) {
                        found.add(record.value());
                    }
                });
            }
        }
        assertThat(found).as("message on order-events.DLT").hasSize(1);
        return found.getFirst();
    }
}
