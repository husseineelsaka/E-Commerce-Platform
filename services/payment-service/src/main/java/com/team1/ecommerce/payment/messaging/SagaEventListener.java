package com.team1.ecommerce.payment.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team1.ecommerce.payment.service.SagaPaymentService;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Charges when stock is reserved; other inventory events are not Payment's concern. */
@Component
public class SagaEventListener {
    private final ObjectMapper mapper;
    private final SagaPaymentService payments;

    public SagaEventListener(ObjectMapper mapper, SagaPaymentService payments) {
        this.mapper = mapper;
        this.payments = payments;
    }

    @KafkaListener(topics = "inventory-events", groupId = "payment-service")
    public void onEvent(String payload) throws JsonProcessingException {
        JsonNode event = mapper.readTree(payload);
        if ("InventoryReserved".equals(event.path("eventType").asText())) {
            payments.inventoryReserved(UUID.fromString(event.path("eventId").asText()),
                    UUID.fromString(event.path("orderId").asText()), event.path("totalAmount").decimalValue());
        }
    }
}
