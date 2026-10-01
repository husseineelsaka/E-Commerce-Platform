package com.team1.ecommerce.payment.service;

import com.team1.ecommerce.payment.entity.Payment;
import com.team1.ecommerce.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payment's side of the Saga (S9): charge once per reserved order and publish the outcome.
 * The orderId is the idempotency key (ADD §3.4), so an order gets exactly one payment and one outcome event.
 */
@Service
public class SagaPaymentService {
    private static final Logger log = LoggerFactory.getLogger(SagaPaymentService.class);

    private final PaymentRepository payments;
    private final ResilientCharge charge;
    private final OutboxWriter outbox;
    private final JdbcTemplate jdbc;

    public SagaPaymentService(PaymentRepository payments, ResilientCharge charge, OutboxWriter outbox, JdbcTemplate jdbc) {
        this.payments = payments;
        this.charge = charge;
        this.outbox = outbox;
        this.jdbc = jdbc;
    }

    @Transactional
    public void inventoryReserved(UUID eventId, UUID orderId, BigDecimal totalAmount) {
        if (!firstDelivery(eventId)) {
            return;
        }
        if (payments.findByOrderId(orderId).isPresent()) {
            log.info("Order {} already has a payment; no second charge", orderId);
            return;
        }
        BigDecimal amount = totalAmount.setScale(2, RoundingMode.UNNECESSARY);
        Payment payment = payments.saveAndFlush(new Payment(orderId, orderId.toString(), hash(orderId, amount), amount));
        if (charge.charge()) {
            payment.complete();
            outbox.append("PaymentCompleted", orderId, Map.of("paymentId", payment.getId(), "amount", amount));
        } else {
            outbox.append("PaymentFailed", orderId, Map.of("paymentId", payment.getId(), "reason", "Payment declined"));
        }
    }

    private boolean firstDelivery(UUID eventId) {
        boolean first = jdbc.update("INSERT INTO processed_event (event_id) VALUES (?) ON CONFLICT DO NOTHING", eventId) == 1;
        if (!first) {
            log.info("Duplicate event {} ignored", eventId);
        }
        return first;
    }

    /** Same request hash as the HTTP API (ADD §3.4): SHA-256 of orderId + ":" + amount. */
    private static String hash(UUID orderId, BigDecimal amount) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest((orderId + ":" + amount.toPlainString()).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
