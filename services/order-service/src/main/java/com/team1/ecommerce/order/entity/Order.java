package com.team1.ecommerce.order.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {
    @Id private UUID id;
    @Column(nullable = false) private String customerId;
    @Column(nullable = false, length = 20) private String status;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal totalAmount;
    @Column(nullable = false) private Instant createdAt;
    @Version private Long version;
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {}
    public Order(UUID id, String customerId, BigDecimal totalAmount, Instant createdAt) {
        this.id = id;
        this.customerId = customerId;
        this.status = "PENDING";
        this.totalAmount = totalAmount;
        this.createdAt = createdAt;
    }
    public void addItem(Long productId, int quantity, BigDecimal unitPrice) {
        items.add(new OrderItem(this, productId, quantity, unitPrice));
    }
    public UUID getId() { return id; }
    public String getCustomerId() { return customerId; }
    public String getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public Instant getCreatedAt() { return createdAt; }
    public List<OrderItem> getItems() { return items; }
}
