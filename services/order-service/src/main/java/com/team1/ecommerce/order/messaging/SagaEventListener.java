package com.team1.ecommerce.order.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team1.ecommerce.order.service.OrderSagaService;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Consumes Inventory and Payment outcomes; other event types on those topics are not Order's concern. */
@Component
public class SagaEventListener {
    private final ObjectMapper mapper;
    private final OrderSagaService saga;

    public SagaEventListener(ObjectMapper mapper, OrderSagaService saga) {
        this.mapper = mapper;
        this.saga = saga;
    }

    @KafkaListener(topics = {"inventory-events", "payment-events"}, groupId = "order-service")
    public void onEvent(String payload) throws JsonProcessingException {
        JsonNode event = mapper.readTree(payload);
        UUID eventId = UUID.fromString(event.path("eventId").asText());
        UUID orderId = UUID.fromString(event.path("orderId").asText());
        switch (event.path("eventType").asText()) {
            case "PaymentCompleted" -> saga.paymentCompleted(eventId, orderId);
            case "PaymentFailed" -> saga.cancel(eventId, orderId, event.path("reason").asText("Payment failed"));
            case "InventoryReservationFailed" -> saga.cancel(eventId, orderId, event.path("reason").asText("Out of stock"));
            default -> { }
        }
    }
}
