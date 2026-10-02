package com.team1.ecommerce.review.controller;

import com.team1.ecommerce.review.dto.ReviewPage;
import com.team1.ecommerce.review.dto.ReviewRequest;
import com.team1.ecommerce.review.dto.ReviewView;
import com.team1.ecommerce.review.service.ReviewService;
import jakarta.validation.Valid;
import java.net.URI;
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
@RequestMapping("/api/v1/products/{productId}/reviews")
public class ReviewController {
    private final ReviewService service;

    public ReviewController(ReviewService service) {
        this.service = service;
    }

    /** The customer is the Gateway-injected identity, never a field in the body (ADD §3.2). */
    @PostMapping
    public ResponseEntity<ReviewView> submit(@RequestHeader("X-User-Id") String customerId,
            @PathVariable Long productId, @Valid @RequestBody ReviewRequest request) {
        ReviewView review = service.submit(productId, customerId, request);
        return ResponseEntity.created(URI.create("/api/v1/products/" + productId + "/reviews/" + review.reviewId()))
                .body(review);
    }

    @GetMapping
    public ReviewPage list(@PathVariable Long productId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(productId, page, size);
    }
}
