package com.team1.ecommerce.inventory.dto;

public record StockView(Long productId, int available, int reserved) {}
