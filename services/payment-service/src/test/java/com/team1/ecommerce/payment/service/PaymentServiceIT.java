package com.team1.ecommerce.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.team1.ecommerce.payment.dto.PaymentRequest;
import com.team1.ecommerce.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class PaymentServiceIT {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired PaymentService service;
    @Autowired PaymentRepository repository;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean ChargeSimulator charge;

    @AfterEach
    void clean() { repository.deleteAll(); reset(charge); }

    @Test
    void uniqueOrderConstraintRejectsSecondRow() {
        UUID order = UUID.randomUUID();
        insert(order, "first");
        assertThatThrownBy(() -> insert(order, "second")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void uniqueKeyConstraintRejectsSecondRow() {
        insert(UUID.randomUUID(), "shared");
        assertThatThrownBy(() -> insert(UUID.randomUUID(), "shared")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void repeatReturnsStoredRow() {
        when(charge.charge()).thenReturn(true);
        var request = request();
        var first = service.create("repeat", request);
        var repeat = service.create("repeat", request);
        assertThat(repeat.payment()).isEqualTo(first.payment());
        assertThat(repeat.created()).isFalse();
        assertThat(repository.count()).isEqualTo(1);
        verify(charge, times(1)).charge();
    }

    @Test
    void concurrentSameKeyChargesOnce() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(charge.charge()).thenAnswer(invocation -> {
            entered.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("charge was not released");
            return true;
        });
        var request = request();
        try (var threads = Executors.newFixedThreadPool(2)) {
            var first = threads.submit(() -> service.create("concurrent", request));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            var second = threads.submit(() -> service.create("concurrent", request));
            release.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS).payment()).isEqualTo(second.get(10, TimeUnit.SECONDS).payment());
        }
        assertThat(repository.count()).isEqualTo(1);
        verify(charge, times(1)).charge();
    }

    @Test
    void completedPaymentCanBeRefundedOnce() {
        when(charge.charge()).thenReturn(true);
        var id = service.create("refund", request()).payment().paymentId();
        assertThat(service.refund(id).status()).isEqualTo("REFUNDED");
        assertThatThrownBy(() -> service.refund(id)).isInstanceOf(PaymentConflictException.class);
    }

    @Test
    void failedPaymentCannotBeRefunded() {
        when(charge.charge()).thenReturn(false);
        var id = service.create("failed", request()).payment().paymentId();
        assertThatThrownBy(() -> service.refund(id)).isInstanceOf(PaymentConflictException.class);
    }

    @Test
    void unknownRefundReturnsNotFound() {
        assertThatThrownBy(() -> service.refund(UUID.randomUUID())).isInstanceOf(PaymentNotFoundException.class);
    }

    private PaymentRequest request() { return new PaymentRequest(UUID.randomUUID(), new BigDecimal("12.50")); }

    private void insert(UUID order, String key) {
        jdbc.update("INSERT INTO payment (id, order_id, idempotency_key, request_hash, amount, status, created_at, version) "
                + "VALUES (?, ?, ?, ?, ?, ?, now(), 0)", UUID.randomUUID(), order, key, "a".repeat(64),
                new BigDecimal("12.50"), "COMPLETED");
    }
}
