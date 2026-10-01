package com.team1.ecommerce.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Delivers a notice to the customer. The handbook allows log-based notifications; a real e-mail or SMS gateway would
 * replace the log line. {@code notification.simulation.fail} makes every send fail to demonstrate retries and the DLT.
 */
@Component
public class NotificationSender {
    private static final Logger log = LoggerFactory.getLogger(NotificationSender.class);

    private final boolean fail;

    public NotificationSender(@Value("${notification.simulation.fail:false}") boolean fail) {
        this.fail = fail;
    }

    public void send(String customerId, String message) {
        if (fail) {
            throw new IllegalStateException("Simulated notification gateway failure");
        }
        log.info("NOTIFICATION to customer {}: {}", customerId, message);
    }
}
