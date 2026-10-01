package com.team1.ecommerce.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record OrderRequest(@NotEmpty List<@Valid Item> items) {
    public record Item(@NotNull @Min(1) Long productId, @NotNull @Min(1) Integer quantity) {}
}
