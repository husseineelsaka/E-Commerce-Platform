package com.team1.ecommerce.payment.service;

import com.team1.ecommerce.payment.dto.PaymentRequest;
import com.team1.ecommerce.payment.dto.PaymentResponse;
import com.team1.ecommerce.payment.entity.Payment;
import com.team1.ecommerce.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PaymentService {
    public record CreatedPayment(PaymentResponse payment, boolean created) { }

    private final PaymentRepository payments;
    private final ChargeSimulator charge;
    private final TransactionTemplate transactions;

    public PaymentService(PaymentRepository payments, ChargeSimulator charge, PlatformTransactionManager manager) {
        this.payments = payments;
        this.charge = charge;
        this.transactions = new TransactionTemplate(manager);
    }

    public CreatedPayment create(String key, PaymentRequest request) {
        BigDecimal amount = request.amount().setScale(2);
        String hash = hash(request.orderId(), amount);
        Optional<Payment> existing = payments.findByIdempotencyKey(key);
        if (existing.isPresent()) return repeated(existing.get(), hash);
        Optional<Payment> forOrder = payments.findByOrderId(request.orderId());
        if (forOrder.isPresent()) {
            // A concurrent request with the same key may have committed between the two lookups.
            if (forOrder.get().getIdempotencyKey().equals(key)) return repeated(forOrder.get(), hash);
            throw new PaymentConflictException();
        }
        try {
            Payment created = transactions.execute(status -> {
                Payment payment = payments.saveAndFlush(new Payment(request.orderId(), key, hash, amount));
                if (charge.charge()) payment.complete();
                return payments.saveAndFlush(payment);
            });
            return new CreatedPayment(PaymentResponse.from(created), true);
        } catch (DataIntegrityViolationException duplicate) {
            // The insert has rolled back; inspect the winner in a fresh transaction.
            return payments.findByIdempotencyKey(key)
                    .map(payment -> repeated(payment, hash))
                    .orElseThrow(PaymentConflictException::new);
        }
    }

    public PaymentResponse refund(UUID id) {
        return transactions.execute(status -> {
            Payment payment = payments.findById(id).orElseThrow(PaymentNotFoundException::new);
            if (payment.getStatus() != Payment.Status.COMPLETED) throw new PaymentConflictException();
            payment.refund();
            return PaymentResponse.from(payments.saveAndFlush(payment));
        });
    }

    private CreatedPayment repeated(Payment payment, String hash) {
        if (!payment.getRequestHash().equals(hash)) throw new PaymentMismatchException();
        return new CreatedPayment(PaymentResponse.from(payment), false);
    }

    private String hash(UUID orderId, BigDecimal amount) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest((orderId + ":" + amount.toPlainString()).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
