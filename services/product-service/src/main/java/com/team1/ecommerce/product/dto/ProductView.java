package com.team1.ecommerce.product.dto;

import java.math.BigDecimal;

/** Product response with category name, never the JPA entity. */
public record ProductView(Long id, String name, BigDecimal price, Long categoryId, String categoryName) {
}
