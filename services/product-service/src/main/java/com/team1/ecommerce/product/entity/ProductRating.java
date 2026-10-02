package com.team1.ecommerce.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Read-only mapping of the rating projection; ProductRatingProjection writes it with SQL. */
@Entity
@Table(name = "product_rating")
public class ProductRating {
    @Id
    @Column(name = "product_id")
    private Long productId;
    @Column(name = "review_count", nullable = false)
    private Integer reviewCount;
    @Column(name = "rating_sum", nullable = false)
    private Long ratingSum;

    protected ProductRating() {
    }
}
