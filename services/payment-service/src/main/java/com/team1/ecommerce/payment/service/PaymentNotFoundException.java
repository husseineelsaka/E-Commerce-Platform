package com.team1.ecommerce.payment.service;

public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException() { super("Payment was not found"); }
}
