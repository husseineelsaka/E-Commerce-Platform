package com.team1.ecommerce.inventory.service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inventory's side of the Saga (S10, FR-07): reserve stock for a placed order, release it when payment fails.
 * Every event is deduplicated by eventId; the dedup row, the stock change, and the outbound event share one local
 * transaction, so redelivery never changes stock twice.
 */
@Service
public class ReservationService {
    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final JdbcTemplate jdbc;
    private final OutboxWriter outbox;

    public ReservationService(JdbcTemplate jdbc, OutboxWriter outbox) {
        this.jdbc = jdbc;
        this.outbox = outbox;
    }

    /**
     * Reserves every line or none. The order's stock rows are locked in product-id order (no deadlock between
     * concurrent orders), checked, and only then changed.
     */
    @Transactional
    public void reserve(UUID eventId, UUID orderId, List<Line> lines, BigDecimal totalAmount) {
        if (!firstDelivery(eventId)) {
            return;
        }
        List<Line> sorted = lines.stream().sorted(Comparator.comparing(Line::productId)).toList();
        Map<Long, Integer> available = lockStock(sorted);
        Line missing = sorted.stream()
                .filter(line -> available.getOrDefault(line.productId(), 0) < line.quantity())
                .findFirst().orElse(null);
        if (missing != null) {
            outbox.append("InventoryReservationFailed", orderId,
                    Map.of("reason", "Insufficient stock for product " + missing.productId()));
            return;
        }
        for (Line line : sorted) {
            jdbc.update("UPDATE stock SET available = available - ?, reserved = reserved + ?, version = version + 1 WHERE product_id = ?",
                    line.quantity(), line.quantity(), line.productId());
            jdbc.update("INSERT INTO reservation (order_id, product_id, quantity, status) VALUES (?, ?, ?, 'RESERVED')",
                    orderId, line.productId(), line.quantity());
        }
        outbox.append("InventoryReserved", orderId, Map.of("totalAmount", totalAmount));
    }

    /** Returns the order's reserved stock to {@code available} (compensation for PaymentFailed). */
    @Transactional
    public void release(UUID eventId, UUID orderId) {
        if (!firstDelivery(eventId)) {
            return;
        }
        List<Line> held = jdbc.query(
                "SELECT product_id, quantity FROM reservation WHERE order_id = ? AND status = 'RESERVED' ORDER BY product_id FOR UPDATE",
                (rs, i) -> new Line(rs.getLong(1), rs.getInt(2)), orderId);
        if (held.isEmpty()) {
            log.info("Order {} has no reserved stock; release ignored", orderId);
            return;
        }
        for (Line line : held) {
            jdbc.update("UPDATE stock SET available = available + ?, reserved = reserved - ?, version = version + 1 WHERE product_id = ?",
                    line.quantity(), line.quantity(), line.productId());
        }
        jdbc.update("UPDATE reservation SET status = 'RELEASED', updated_at = now() WHERE order_id = ? AND status = 'RESERVED'", orderId);
        outbox.append("InventoryReleased", orderId, Map.of());
    }

    private Map<Long, Integer> lockStock(List<Line> lines) {
        String ids = lines.stream().map(line -> "?").collect(Collectors.joining(","));
        return jdbc.query("SELECT product_id, available FROM stock WHERE product_id IN (" + ids + ") ORDER BY product_id FOR UPDATE",
                        (rs, i) -> Map.entry(rs.getLong(1), rs.getInt(2)), lines.stream().map(Line::productId).toArray())
                .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private boolean firstDelivery(UUID eventId) {
        boolean first = jdbc.update("INSERT INTO processed_event (event_id) VALUES (?) ON CONFLICT DO NOTHING", eventId) == 1;
        if (!first) {
            log.info("Duplicate event {} ignored", eventId);
        }
        return first;
    }

    public record Line(Long productId, int quantity) {}
}
