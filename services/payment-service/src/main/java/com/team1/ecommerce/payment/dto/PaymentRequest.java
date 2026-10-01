package com.team1.ecommerce.payment.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record PaymentRequest(@NotNull UUID orderId,
                             @NotNull @DecimalMin(value = "0.00", inclusive = false)
                             @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2) BigDecimal amount) {
}
