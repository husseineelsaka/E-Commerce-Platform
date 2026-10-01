package com.team1.ecommerce.inventory.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team1.ecommerce.inventory.service.ReservationService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Reserves stock on OrderPlaced and releases it on PaymentFailed; other event types are ignored. */
@Component
public class SagaEventListener {
    private final ObjectMapper mapper;
    private final ReservationService reservations;

    public SagaEventListener(ObjectMapper mapper, ReservationService reservations) {
        this.mapper = mapper;
        this.reservations = reservations;
    }

    @KafkaListener(topics = {"order-events", "payment-events"}, groupId = "inventory-service")
    public void onEvent(String payload) throws JsonProcessingException {
        JsonNode event = mapper.readTree(payload);
        UUID eventId = UUID.fromString(event.path("eventId").asText());
        UUID orderId = UUID.fromString(event.path("orderId").asText());
        switch (event.path("eventType").asText()) {
            case "OrderPlaced" -> reservations.reserve(eventId, orderId, lines(event), event.path("totalAmount").decimalValue());
            case "PaymentFailed" -> reservations.release(eventId, orderId);
            default -> { }
        }
    }

    private List<ReservationService.Line> lines(JsonNode event) {
        List<ReservationService.Line> lines = new ArrayList<>();
        event.path("items").forEach(item ->
                lines.add(new ReservationService.Line(item.path("productId").asLong(), item.path("quantity").asInt())));
        return lines;
    }
}
