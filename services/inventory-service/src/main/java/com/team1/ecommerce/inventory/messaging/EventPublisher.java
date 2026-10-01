package com.team1.ecommerce.inventory.messaging;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Sends Inventory's events to {@code inventory-events}, keyed by orderId. */
@Component
public class EventPublisher {
    public static final String TOPIC = "inventory-events";

    private final KafkaTemplate<String, String> kafka;

    public EventPublisher(KafkaTemplate<String, String> kafka) {
        this.kafka = kafka;
    }

    public void publish(String key, String payload) {
        try {
            kafka.send(TOPIC, key, payload).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing to " + TOPIC, exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("Could not publish to " + TOPIC, exception);
        }
    }
}
