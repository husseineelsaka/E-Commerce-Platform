package com.team1.ecommerce.order.repository;

import com.team1.ecommerce.order.entity.Order;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    Optional<Order> findByIdAndCustomerId(UUID id, String customerId);
    Page<Order> findByCustomerId(String customerId, Pageable pageable);
}
