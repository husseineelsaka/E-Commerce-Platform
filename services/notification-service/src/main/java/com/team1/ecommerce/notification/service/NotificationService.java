package com.team1.ecommerce.notification.service;

import org.springframework.stereotype.Service;

/** Turns order outcomes into customer notices (FR-11). */
@Service
public class NotificationService {
    private final NotificationSender sender;

    public NotificationService(NotificationSender sender) {
        this.sender = sender;
    }

    public void orderConfirmed(String orderId, String customerId) {
        sender.send(customerId, "Your order " + orderId + " is confirmed.");
    }

    public void orderCancelled(String orderId, String customerId, String reason) {
        sender.send(customerId, "Your order " + orderId + " was cancelled: " + reason + ".");
    }
}
