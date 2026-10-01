package com.team1.ecommerce.inventory.exception;

public class StockNotFoundException extends RuntimeException {
    public StockNotFoundException(Long productId) {
        super("Stock for product " + productId + " was not found");
    }
}
