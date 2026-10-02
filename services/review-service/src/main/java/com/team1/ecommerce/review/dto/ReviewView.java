package com.team1.ecommerce.review.dto;

import com.team1.ecommerce.review.entity.Review;
import java.time.Instant;
import java.util.UUID;

/** A review as the API returns it; the customer ID stays internal. */
public record ReviewView(UUID reviewId, Long productId, int rating, String text, Instant createdAt) {
    public static ReviewView from(Review review) {
        return new ReviewView(review.getId(), review.getProductId(), review.getRating(), review.getText(),
                review.getCreatedAt());
    }
}
