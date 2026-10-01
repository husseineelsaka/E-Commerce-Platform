package com.team1.ecommerce.order.client;

import java.math.BigDecimal;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "product-service", url = "${order.clients.product-url:}")
public interface ProductClient {
    @GetMapping("/api/v1/products/{id}")
    ProductPrice find(@PathVariable("id") Long id);
    record ProductPrice(BigDecimal price) {}
}
