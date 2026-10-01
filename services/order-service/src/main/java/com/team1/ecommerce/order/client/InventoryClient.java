package com.team1.ecommerce.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "inventory-service", url = "${order.clients.inventory-url:}")
public interface InventoryClient {
    @GetMapping("/api/v1/inventory/check")
    Availability check(@RequestParam("productId") Long productId, @RequestParam("quantity") int quantity);
    record Availability(boolean available) {}
}
