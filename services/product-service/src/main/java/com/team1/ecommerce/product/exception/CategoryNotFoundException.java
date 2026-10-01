package com.team1.ecommerce.product.exception;

public class CategoryNotFoundException extends RuntimeException {
    public CategoryNotFoundException(Long id) {
        super("Category " + id + " was not found");
    }
}
