package com.team1.ecommerce.product.exception;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(Long id) {
        super("Product " + id + " was not found");
    }
}
