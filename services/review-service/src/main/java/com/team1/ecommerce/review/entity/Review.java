package com.team1.ecommerce.review.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "review")
public class Review {
    @Id
    private UUID id;
    @Column(name = "product_id", nullable = false)
    private Long productId;
    @Column(name = "customer_id", nullable = false)
    private String customerId;
    @Column(nullable = false)
    private short rating;
    @Column(nullable = false)
    private String text;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Review() {
    }

    public Review(Long productId, String customerId, int rating, String text) {
        this.id = UUID.randomUUID();
        this.productId = productId;
        this.customerId = customerId;
        this.rating = (short) rating;
        this.text = text;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Long getProductId() { return productId; }
    public String getCustomerId() { return customerId; }
    public int getRating() { return rating; }
    public String getText() { return text; }
    public Instant getCreatedAt() { return createdAt; }
}
