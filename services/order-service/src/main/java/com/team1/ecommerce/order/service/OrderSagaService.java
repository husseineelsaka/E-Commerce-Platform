package com.team1.ecommerce.order.service;

import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saga outcomes for Order (choreography, ADD §5): an order leaves PENDING exactly once.
 * Each event is deduplicated by eventId; the dedup row, the state change, and the outbound event are one local
 * transaction. A duplicate event, or a late event for an order that is no longer PENDING, changes nothing.
 */
@Service
public class OrderSagaService {
    private static final Logger log = LoggerFactory.getLogger(OrderSagaService.class);

    private final JdbcTemplate jdbc;
    private final OutboxWriter outbox;

    public OrderSagaService(JdbcTemplate jdbc, OutboxWriter outbox) {
        this.jdbc = jdbc;
        this.outbox = outbox;
    }

    @Transactional
    public void paymentCompleted(UUID eventId, UUID orderId) {
        if (firstDelivery(eventId) && leavePending(orderId, "CONFIRMED")) {
            outbox.append("OrderConfirmed", orderId, Map.of("customerId", customerOf(orderId)));
        }
    }

    @Transactional
    public void cancel(UUID eventId, UUID orderId, String reason) {
        if (firstDelivery(eventId) && leavePending(orderId, "CANCELLED")) {
            outbox.append("OrderCancelled", orderId, Map.of("customerId", customerOf(orderId), "reason", reason));
        }
    }

    private boolean firstDelivery(UUID eventId) {
        boolean first = jdbc.update("INSERT INTO processed_event (event_id) VALUES (?) ON CONFLICT DO NOTHING", eventId) == 1;
        if (!first) {
            log.info("Duplicate event {} ignored", eventId);
        }
        return first;
    }

    private boolean leavePending(UUID orderId, String status) {
        boolean changed = jdbc.update("UPDATE orders SET status = ?, version = version + 1 WHERE id = ? AND status = 'PENDING'",
                status, orderId) == 1;
        if (!changed) {
            log.info("Order {} is not PENDING; {} ignored", orderId, status);
        }
        return changed;
    }

    private String customerOf(UUID orderId) {
        return jdbc.queryForObject("SELECT customer_id FROM orders WHERE id = ?", String.class, orderId);
    }
}
