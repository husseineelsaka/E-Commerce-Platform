package com.team1.ecommerce.product.dto;

import java.math.BigDecimal;

/** Read model for product reads (FR-03): product fields plus the category name, never the JPA entity. */
public record ProductView(Long id, String name, BigDecimal price, Long categoryId, String categoryName) {
}
