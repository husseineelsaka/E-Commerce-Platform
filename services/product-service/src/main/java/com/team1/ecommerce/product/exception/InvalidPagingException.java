package com.team1.ecommerce.product.exception;

public class InvalidPagingException extends RuntimeException {
    public InvalidPagingException() {
        super("page must be at least 0 and size must be between 1 and 100");
    }
}
