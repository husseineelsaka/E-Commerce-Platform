package com.team1.ecommerce.payment.service;

import com.team1.ecommerce.payment.messaging.EventPublisher;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Publishes unpublished outbox rows in creation order and marks them published (polling publisher, ADD §5). */
@Service
public class OutboxRelay {
    private final JdbcTemplate jdbc;
    private final EventPublisher publisher;
    private final Tracer tracer;
    private final Propagator propagator;

    public OutboxRelay(JdbcTemplate jdbc, EventPublisher publisher, Tracer tracer, Propagator propagator) {
        this.jdbc = jdbc;
        this.publisher = publisher;
        this.tracer = tracer;
        this.propagator = propagator;
    }

    /** SKIP LOCKED keeps instances apart; a failed send rolls back and is retried on the next poll (ADD §6 F7, F8). */
    @Transactional
    public int publishPending() {
        List<Row> rows = jdbc.query("""
                SELECT id, aggregate_id, payload::text, event_type, trace_parent FROM outbox_event
                WHERE published_at IS NULL ORDER BY created_at LIMIT 100 FOR UPDATE SKIP LOCKED""",
                (rs, i) -> new Row(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                        rs.getString(4), rs.getString(5)));
        for (Row row : rows) {
            publishInOriginalTrace(row);
            jdbc.update("UPDATE outbox_event SET published_at = now() WHERE id = ?", row.id());
        }
        return rows.size();
    }

    /**
     * Continues the trace of the transaction that wrote the row (NFR-06), so one trace spans the HTTP request, the
     * outbox hop, and every Kafka consumer. Rows without a stored traceparent are sent in a new trace.
     */
    private void publishInOriginalTrace(Row row) {
        Span span = (row.traceParent() == null ? tracer.spanBuilder()
                : propagator.extract(Map.of("traceparent", row.traceParent()), Map::get))
                .name("outbox publish " + row.eventType()).start();
        try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
            publisher.publish(row.aggregateId().toString(), row.payload());
        } finally {
            span.end();
        }
    }

    private record Row(UUID id, UUID aggregateId, String payload, String eventType, String traceParent) {}
}
