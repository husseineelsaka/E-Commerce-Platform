package com.team1.ecommerce.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** The order, its items, and its OrderPlaced outbox row are one local transaction (real PostgreSQL + Flyway). */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class OrderServiceIT {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.9");

    @Autowired OrderWriter writer;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @AfterEach
    void clean() {
        jdbc.execute("ALTER TABLE outbox_event DROP CONSTRAINT IF EXISTS reject_all");
        jdbc.execute("DELETE FROM outbox_event; DELETE FROM order_item; DELETE FROM orders");
    }

    @Test
    void savesOrderAndOutboxTogether() throws Exception {
        UUID orderId = writer.save("customer-1", List.of(
                new OrderWriter.PricedItem(1L, 2, new BigDecimal("12.50")),
                new OrderWriter.PricedItem(3L, 1, new BigDecimal("49.00"))));

        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT total_amount FROM orders WHERE id = ?", BigDecimal.class, orderId))
                .isEqualByComparingTo("74.00");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_item WHERE order_id = ?", Integer.class, orderId)).isEqualTo(2);

        JsonNode event = mapper.readTree(jdbc.queryForObject(
                "SELECT payload::text FROM outbox_event WHERE aggregate_id = ? AND event_type = 'OrderPlaced' AND published_at IS NULL",
                String.class, orderId));
        assertThat(event.get("orderId").asText()).isEqualTo(orderId.toString());
        assertThat(event.get("customerId").asText()).isEqualTo("customer-1");
        assertThat(event.get("version").asInt()).isEqualTo(1);
        assertThat(event.get("items")).hasSize(2);
        assertThat(event.get("totalAmount").decimalValue()).isEqualByComparingTo("74.00");
    }

    @Test
    void failedOutboxInsertLeavesNoOrder() {
        jdbc.execute("ALTER TABLE outbox_event ADD CONSTRAINT reject_all CHECK (false) NOT VALID");

        assertThatThrownBy(() -> writer.save("customer-1", List.of(new OrderWriter.PricedItem(1L, 1, new BigDecimal("12.50")))))
                .isInstanceOf(RuntimeException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_item", Integer.class)).isZero();
    }
}
