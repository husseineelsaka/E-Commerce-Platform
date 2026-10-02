package com.team1.ecommerce.review.messaging;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Sends Review's events to {@code review-events}, keyed by reviewId. */
@Component
public class EventPublisher {
    public static final String TOPIC = "review-events";

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
