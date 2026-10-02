package com.team1.ecommerce.inventory.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Appends an event to {@code outbox_event} inside the caller's transaction (transactional outbox, ADD §5). */
@Component
public class OutboxWriter {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Tracer tracer;
    private final Propagator propagator;

    public OutboxWriter(JdbcTemplate jdbc, ObjectMapper mapper, Tracer tracer, Propagator propagator) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.tracer = tracer;
        this.propagator = propagator;
    }

    /** Adds the common envelope (eventId, eventType, version, occurredAt, orderId) to the business fields (ADD §3.3). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String eventType, UUID orderId, Map<String, Object> fields) {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", eventId);
        event.put("eventType", eventType);
        event.put("version", 1);
        event.put("occurredAt", now);
        event.put("orderId", orderId);
        event.putAll(fields);
        try {
            jdbc.update("INSERT INTO outbox_event (id, aggregate_id, event_type, payload, created_at, trace_parent) VALUES (?, ?, ?, ?::jsonb, ?, ?)",
                    eventId, orderId, eventType, mapper.writeValueAsString(event), Timestamp.from(now), currentTraceParent());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize " + eventType, exception);
        }
    }

    /** The W3C traceparent of the current span, or null outside a trace. */
    private String currentTraceParent() {
        var context = tracer.currentTraceContext().context();
        if (context == null) {
            return null;
        }
        Map<String, String> carrier = new LinkedHashMap<>();
        propagator.inject(context, carrier, Map::put);
        return carrier.get("traceparent");
    }
}
