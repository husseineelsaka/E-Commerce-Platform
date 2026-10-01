package com.team1.ecommerce.inventory.service;

import com.team1.ecommerce.inventory.messaging.EventPublisher;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Publishes unpublished outbox rows in creation order and marks them published (polling publisher, ADD §5). */
@Service
public class OutboxRelay {
    private final JdbcTemplate jdbc;
    private final EventPublisher publisher;

    public OutboxRelay(JdbcTemplate jdbc, EventPublisher publisher) {
        this.jdbc = jdbc;
        this.publisher = publisher;
    }

    /** SKIP LOCKED keeps instances apart; a failed send rolls back and is retried on the next poll (ADD §6 F7, F8). */
    @Transactional
    public int publishPending() {
        List<Row> rows = jdbc.query("""
                SELECT id, aggregate_id, payload::text FROM outbox_event
                WHERE published_at IS NULL ORDER BY created_at LIMIT 100 FOR UPDATE SKIP LOCKED""",
                (rs, i) -> new Row(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3)));
        for (Row row : rows) {
            publisher.publish(row.aggregateId().toString(), row.payload());
            jdbc.update("UPDATE outbox_event SET published_at = now() WHERE id = ?", row.id());
        }
        return rows.size();
    }

    private record Row(UUID id, UUID aggregateId, String payload) {}
}
