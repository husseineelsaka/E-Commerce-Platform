package com.team1.ecommerce.payment.controller;

import com.team1.ecommerce.payment.dto.PaymentRequest;
import com.team1.ecommerce.payment.dto.PaymentResponse;
import com.team1.ecommerce.payment.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentService payments;

    public PaymentController(PaymentService payments) { this.payments = payments; }

    @PostMapping
    public ResponseEntity<PaymentResponse> create(@RequestHeader("Idempotency-Key") @Size(min = 1, max = 100) String key,
                                                   @Valid @RequestBody PaymentRequest request) {
        var result = payments.create(key, request);
        if (result.created()) {
            return ResponseEntity.created(URI.create("/api/v1/payments/" + result.payment().paymentId()))
                    .body(result.payment());
        }
        return ResponseEntity.ok(result.payment());
    }

    @PostMapping("/{id}/refund")
    public PaymentResponse refund(@PathVariable UUID id) { return payments.refund(id); }
}
