package com.team1.ecommerce.review.exception;

import org.springframework.http.HttpStatus;

public class ReviewException extends RuntimeException {
    private final HttpStatus status;

    public ReviewException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() { return status; }
}
