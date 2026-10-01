package com.team1.ecommerce.payment.service;

public class PaymentMismatchException extends RuntimeException {
    public PaymentMismatchException() { super("Idempotency-Key was used with a different request"); }
}
