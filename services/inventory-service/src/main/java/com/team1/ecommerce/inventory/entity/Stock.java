package com.team1.ecommerce.inventory.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Version;

@Entity
public class Stock {
    @Id
    private Long productId;
    private int available;
    private int reserved;
    @Version
    private Long version;

    protected Stock() {}

    public Stock(Long productId, int available) {
        this.productId = productId;
        this.available = available;
    }

    public Long getProductId() { return productId; }
    public int getAvailable() { return available; }
    public int getReserved() { return reserved; }
    public void setAvailable(int available) { this.available = available; }
}
