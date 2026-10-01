package com.team1.ecommerce.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team1.ecommerce.order.dto.OrderRequest;
import com.team1.ecommerce.order.entity.Order;
import com.team1.ecommerce.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderWriter {
    private final OrderRepository orders;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public OrderWriter(OrderRepository orders, JdbcTemplate jdbc, ObjectMapper mapper) {
        this.orders = orders;
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional
    public UUID save(String customerId, List<PricedItem> items) {
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();
        BigDecimal total = items.stream().map(item -> item.price().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Order order = new Order(orderId, customerId, total, now);
        items.forEach(item -> order.addItem(item.productId(), item.quantity(), item.price()));
        orders.saveAndFlush(order);
        Map<String, Object> event = Map.of("eventId", eventId, "eventType", "OrderPlaced", "version", 1,
                "occurredAt", now, "orderId", orderId, "customerId", customerId,
                "items", items.stream().map(item -> Map.of("productId", item.productId(), "quantity", item.quantity())).toList(),
                "totalAmount", total);
        try {
            jdbc.update("INSERT INTO outbox_event (id, aggregate_id, event_type, payload, created_at) VALUES (?, ?, ?, ?::jsonb, ?)",
                    eventId, orderId, "OrderPlaced", mapper.writeValueAsString(event), Timestamp.from(now));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize OrderPlaced", ex);
        }
        return orderId;
    }

    public record PricedItem(Long productId, int quantity, BigDecimal price) {}
}
