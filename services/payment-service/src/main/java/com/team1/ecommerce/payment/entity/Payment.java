package com.team1.ecommerce.payment.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
public class Payment {
    public enum Status { COMPLETED, FAILED, REFUNDED }

    @Id
    private UUID id;
    private UUID orderId;
    private String idempotencyKey;
    private String requestHash;
    private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    private Status status;
    private Instant createdAt;
    @Version
    private Long version;

    protected Payment() { }

    public Payment(UUID orderId, String idempotencyKey, String requestHash, BigDecimal amount) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.amount = amount;
        this.status = Status.FAILED;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public BigDecimal getAmount() { return amount; }
    public Status getStatus() { return status; }
    public void complete() { status = Status.COMPLETED; }
    public void refund() { status = Status.REFUNDED; }
}
