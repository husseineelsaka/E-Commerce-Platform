package com.team1.ecommerce.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record StockAdjustment(@NotNull @Min(0) Integer available) {}
