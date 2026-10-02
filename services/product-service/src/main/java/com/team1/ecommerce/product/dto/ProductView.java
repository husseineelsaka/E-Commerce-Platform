package com.team1.ecommerce.product.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Product response with category name and rating, never the JPA entity. {@code averageRating} is null until the
 * product has a review.
 */
public record ProductView(Long id, String name, BigDecimal price, Long categoryId, String categoryName,
                          BigDecimal averageRating, long reviewCount) {
    /** Used by the repository's left join on product_rating; both rating columns are null without reviews. */
    public ProductView(Long id, String name, BigDecimal price, Long categoryId, String categoryName,
                       Integer reviewCount, Long ratingSum) {
        this(id, name, price, categoryId, categoryName,
                reviewCount == null ? null
                        : BigDecimal.valueOf(ratingSum).divide(BigDecimal.valueOf(reviewCount), 2, RoundingMode.HALF_UP),
                reviewCount == null ? 0 : reviewCount);
    }
}
