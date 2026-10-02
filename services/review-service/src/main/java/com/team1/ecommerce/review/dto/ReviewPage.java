package com.team1.ecommerce.review.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record ReviewPage(List<ReviewView> content, PageMetadata page) {
    public static ReviewPage from(Page<ReviewView> reviews) {
        return new ReviewPage(reviews.getContent(), new PageMetadata(reviews.getSize(),
                reviews.getNumber(), reviews.getTotalElements(), reviews.getTotalPages()));
    }

    public record PageMetadata(int size, int number, long totalElements, int totalPages) {
    }
}
