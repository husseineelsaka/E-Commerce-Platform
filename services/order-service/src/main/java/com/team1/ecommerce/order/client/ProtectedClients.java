package com.team1.ecommerce.order.client;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshotFactory;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

@Service
public class ProtectedClients {
    /** Carries the caller's trace context onto the TimeLimiter's async thread, so Feign spans join the request trace. */
    private static final ContextSnapshotFactory CONTEXT = ContextSnapshotFactory.builder().build();

    private final ProductClient product;
    private final InventoryClient inventory;

    public ProtectedClients(ProductClient product, InventoryClient inventory) {
        this.product = product;
        this.inventory = inventory;
    }

    @CircuitBreaker(name = "product") @Retry(name = "product")
    @Bulkhead(name = "product") @TimeLimiter(name = "product")
    public CompletableFuture<ProductClient.ProductPrice> price(Long id) {
        return CompletableFuture.supplyAsync(inCallerContext(() -> product.find(id)));
    }

    @CircuitBreaker(name = "inventory") @Retry(name = "inventory")
    @Bulkhead(name = "inventory") @TimeLimiter(name = "inventory")
    public CompletableFuture<InventoryClient.Availability> available(Long id, int quantity) {
        return CompletableFuture.supplyAsync(inCallerContext(() -> inventory.check(id, quantity)));
    }

    private static <T> Supplier<T> inCallerContext(Supplier<T> call) {
        ContextSnapshot snapshot = CONTEXT.captureAll();
        return () -> {
            try (ContextSnapshot.Scope scope = snapshot.setThreadLocals()) {
                return call.get();
            }
        };
    }
}
