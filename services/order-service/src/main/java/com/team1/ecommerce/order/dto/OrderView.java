package com.team1.ecommerce.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderView(UUID orderId, String status, BigDecimal totalAmount, Instant createdAt, List<Item> items) {
    public record Item(Long productId, int quantity, BigDecimal unitPrice) {}
}
