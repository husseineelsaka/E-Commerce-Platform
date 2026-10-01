package com.team1.ecommerce.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.team1.ecommerce.payment.dto.PaymentRequest;
import com.team1.ecommerce.payment.entity.Payment;
import com.team1.ecommerce.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

class PaymentServiceTest {
    @Test
    void sameKeyChargesOnce() {
        PaymentRepository repository = mock(PaymentRepository.class);
        ChargeSimulator charge = mock(ChargeSimulator.class);
        AtomicReference<Payment> stored = new AtomicReference<>();
        when(charge.charge()).thenReturn(true);
        when(repository.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            stored.set(payment);
            return payment;
        });
        when(repository.findByIdempotencyKey("repeat-key")).thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        var service = new PaymentService(repository, charge, mock(PlatformTransactionManager.class));
        var request = new PaymentRequest(UUID.randomUUID(), new BigDecimal("10.00"));

        var first = service.create("repeat-key", request);
        var repeat = service.create("repeat-key", request);

        assertThat(repeat.payment()).isEqualTo(first.payment());
        assertThat(repeat.created()).isFalse();
        verify(charge, times(1)).charge();
    }
}
