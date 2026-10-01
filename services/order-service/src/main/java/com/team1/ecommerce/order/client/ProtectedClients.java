package com.team1.ecommerce.order.client;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Service;

@Service
public class ProtectedClients {
    private final ProductClient product;
    private final InventoryClient inventory;

    public ProtectedClients(ProductClient product, InventoryClient inventory) {
        this.product = product;
        this.inventory = inventory;
    }

    @CircuitBreaker(name = "product") @Retry(name = "product")
    @Bulkhead(name = "product") @TimeLimiter(name = "product")
    public CompletableFuture<ProductClient.ProductPrice> price(Long id) {
        return CompletableFuture.supplyAsync(() -> product.find(id));
    }

    @CircuitBreaker(name = "inventory") @Retry(name = "inventory")
    @Bulkhead(name = "inventory") @TimeLimiter(name = "inventory")
    public CompletableFuture<InventoryClient.Availability> available(Long id, int quantity) {
        return CompletableFuture.supplyAsync(() -> inventory.check(id, quantity));
    }
}
