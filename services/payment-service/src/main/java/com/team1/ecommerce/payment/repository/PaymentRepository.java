package com.team1.ecommerce.payment.repository;

import com.team1.ecommerce.payment.entity.Payment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByIdempotencyKey(String key);
    Optional<Payment> findByOrderId(UUID orderId);
}
