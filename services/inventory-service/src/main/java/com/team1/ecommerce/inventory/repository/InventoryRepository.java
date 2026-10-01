package com.team1.ecommerce.inventory.repository;

import com.team1.ecommerce.inventory.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryRepository extends JpaRepository<Stock, Long> {}
