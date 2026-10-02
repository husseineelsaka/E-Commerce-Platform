package com.team1.ecommerce.product.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team1.ecommerce.product.service.ProductRatingProjection;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Applies ReviewSubmitted to the product's rating; other event types are ignored. */
@Component
public class ReviewEventListener {
    private final ObjectMapper mapper;
    private final ProductRatingProjection ratings;

    public ReviewEventListener(ObjectMapper mapper, ProductRatingProjection ratings) {
        this.mapper = mapper;
        this.ratings = ratings;
    }

    @KafkaListener(topics = "review-events", groupId = "product-service")
    public void onEvent(String payload) throws JsonProcessingException {
        JsonNode event = mapper.readTree(payload);
        if ("ReviewSubmitted".equals(event.path("eventType").asText())) {
            ratings.apply(UUID.fromString(event.path("eventId").asText()),
                    event.path("productId").asLong(), event.path("rating").asInt());
        }
    }
}
