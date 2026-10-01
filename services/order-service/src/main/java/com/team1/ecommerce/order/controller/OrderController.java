package com.team1.ecommerce.order.controller;

import com.team1.ecommerce.order.dto.OrderPage;
import com.team1.ecommerce.order.dto.OrderRequest;
import com.team1.ecommerce.order.dto.OrderView;
import com.team1.ecommerce.order.exception.OrderException;
import com.team1.ecommerce.order.service.OrderService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final OrderService service;
    public OrderController(OrderService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<Map<String, Object>> place(@RequestHeader("X-User-Id") String customerId,
            @Valid @RequestBody OrderRequest request) {
        UUID id = service.place(customerId, request);
        return ResponseEntity.created(URI.create("/api/v1/orders/" + id))
                .body(Map.of("orderId", id, "status", "PENDING"));
    }

    @GetMapping
    public OrderPage list(@RequestHeader("X-User-Id") String customerId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(customerId, page, size);
    }

    @GetMapping("/{id}")
    public OrderView get(@RequestHeader("X-User-Id") String customerId, @PathVariable UUID id) {
        return service.get(id, customerId);
    }
}
