package com.team1.ecommerce.payment.service;

public class PaymentConflictException extends RuntimeException {
    public PaymentConflictException() { super("Order already has a payment or payment cannot be refunded"); }
}
