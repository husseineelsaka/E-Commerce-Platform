package com.team1.ecommerce.payment.dto;

import com.team1.ecommerce.payment.entity.Payment;
import java.math.BigDecimal;
import java.util.UUID;

public record PaymentResponse(UUID paymentId, UUID orderId, BigDecimal amount, String status) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getOrderId(), payment.getAmount(), payment.getStatus().name());
    }
}
