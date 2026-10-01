package com.team1.ecommerce.order.service;

import com.team1.ecommerce.order.entity.Order;
import com.team1.ecommerce.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderWriter {
    private final OrderRepository orders;
    private final OutboxWriter outbox;

    public OrderWriter(OrderRepository orders, OutboxWriter outbox) {
        this.orders = orders;
        this.outbox = outbox;
    }

    /** Saves the PENDING order, its items, and the OrderPlaced outbox row in one transaction. */
    @Transactional
    public UUID save(String customerId, List<PricedItem> items) {
        UUID orderId = UUID.randomUUID();
        BigDecimal total = items.stream().map(item -> item.price().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Order order = new Order(orderId, customerId, total, Instant.now());
        items.forEach(item -> order.addItem(item.productId(), item.quantity(), item.price()));
        orders.saveAndFlush(order);
        outbox.append("OrderPlaced", orderId, Map.of("customerId", customerId,
                "items", items.stream().map(item -> Map.of("productId", item.productId(), "quantity", item.quantity())).toList(),
                "totalAmount", total));
        return orderId;
    }

    public record PricedItem(Long productId, int quantity, BigDecimal price) {}
}
