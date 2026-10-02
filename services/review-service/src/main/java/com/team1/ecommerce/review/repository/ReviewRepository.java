package com.team1.ecommerce.review.repository;

import com.team1.ecommerce.review.entity.Review;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, UUID> {
    Page<Review> findByProductId(Long productId, Pageable pageable);
}
