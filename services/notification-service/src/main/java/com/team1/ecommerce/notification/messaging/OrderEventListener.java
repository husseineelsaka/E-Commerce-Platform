package com.team1.ecommerce.notification.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team1.ecommerce.notification.service.NotificationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.SameIntervalTopicReuseStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/**
 * Notifies customers about OrderConfirmed and OrderCancelled only (never raw payment results). A failing send is
 * retried on {@code order-events-retry} with exponential backoff, then parked on {@code order-events.DLT} (NFR-10).
 */
@Component
public class OrderEventListener {
    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final ObjectMapper mapper;
    private final NotificationService notifications;

    public OrderEventListener(ObjectMapper mapper, NotificationService notifications) {
        this.mapper = mapper;
        this.notifications = notifications;
    }

    @RetryableTopic(attempts = "${notification.retry.attempts:4}",
            backoff = @Backoff(delayExpression = "${notification.retry.delay-ms:1000}", multiplier = 2.0),
            dltTopicSuffix = ".DLT", retryTopicSuffix = "-retry",
            sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.SINGLE_TOPIC)
    @KafkaListener(topics = "order-events", groupId = "notification-service")
    public void onEvent(String payload) throws JsonProcessingException {
        JsonNode event = mapper.readTree(payload);
        String orderId = event.path("orderId").asText();
        String customerId = event.path("customerId").asText();
        switch (event.path("eventType").asText()) {
            case "OrderConfirmed" -> notifications.orderConfirmed(orderId, customerId);
            case "OrderCancelled" -> notifications.orderCancelled(orderId, customerId, event.path("reason").asText("no reason given"));
            default -> { }
        }
    }

    /** Alert log for a notification that could not be delivered; the message stays on the DLT for replay. */
    @DltHandler
    public void onDeadLetter(ConsumerRecord<String, String> record,
                             @Header(name = KafkaHeaders.EXCEPTION_MESSAGE, required = false) String error) {
        log.error("ALERT notification parked on {} for order {}: {}", record.topic(), record.key(), error);
    }
}
