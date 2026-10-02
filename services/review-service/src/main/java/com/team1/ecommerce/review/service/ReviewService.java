package com.team1.ecommerce.review.service;

import com.team1.ecommerce.review.dto.ReviewPage;
import com.team1.ecommerce.review.dto.ReviewRequest;
import com.team1.ecommerce.review.dto.ReviewView;
import com.team1.ecommerce.review.entity.Review;
import com.team1.ecommerce.review.exception.ReviewException;
import com.team1.ecommerce.review.repository.ReviewRepository;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {
    private final ReviewRepository reviews;
    private final OutboxWriter outbox;

    public ReviewService(ReviewRepository reviews, OutboxWriter outbox) {
        this.reviews = reviews;
        this.outbox = outbox;
    }

    /**
     * Stores the review and its ReviewSubmitted event in one transaction (ADD §5). The unique constraint on
     * (product, customer) turns a second review, even a concurrent one, into 409 (ADD §6 F6).
     */
    @Transactional
    public ReviewView submit(Long productId, String customerId, ReviewRequest request) {
        Review review;
        try {
            review = reviews.saveAndFlush(new Review(productId, customerId, request.rating(), request.text().strip()));
        } catch (DataIntegrityViolationException exception) {
            throw new ReviewException(HttpStatus.CONFLICT, "You have already reviewed this product");
        }
        outbox.append("ReviewSubmitted", review.getId(), Map.of(
                "reviewId", review.getId(), "productId", productId,
                "customerId", customerId, "rating", review.getRating()));
        return ReviewView.from(review);
    }

    @Transactional(readOnly = true)
    public ReviewPage list(Long productId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ReviewException(HttpStatus.BAD_REQUEST, "Invalid paging");
        }
        return ReviewPage.from(reviews.findByProductId(productId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"))).map(ReviewView::from));
    }
}
