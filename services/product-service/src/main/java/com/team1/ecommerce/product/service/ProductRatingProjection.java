package com.team1.ecommerce.product.service;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds Product's rating read model from ReviewSubmitted (ADD §2, B1). */
@Service
public class ProductRatingProjection {
    private static final Logger log = LoggerFactory.getLogger(ProductRatingProjection.class);

    private final JdbcTemplate jdbc;
    private final ProductService products;

    public ProductRatingProjection(JdbcTemplate jdbc, ProductService products) {
        this.jdbc = jdbc;
        this.products = products;
    }

    /**
     * Counts one review, once. The processed_event insert and the rating update share the transaction, so a
     * redelivered event changes nothing (ADD §6 F1). An event for a product this service does not have is recorded
     * and skipped; inserting it would break the foreign key and send the event to the DLT (ADD §2).
     */
    @Transactional
    public void apply(UUID eventId, long productId, int rating) {
        if (jdbc.update("INSERT INTO processed_event (event_id) VALUES (?) ON CONFLICT DO NOTHING", eventId) == 0) {
            log.info("Duplicate ReviewSubmitted {} ignored", eventId);
            return;
        }
        if (jdbc.queryForObject("SELECT count(*) FROM product WHERE id = ?", Integer.class, productId) == 0) {
            log.info("ReviewSubmitted {} for unknown product {} skipped", eventId, productId);
            return;
        }
        jdbc.update("""
                INSERT INTO product_rating (product_id, review_count, rating_sum) VALUES (?, 1, ?)
                ON CONFLICT (product_id) DO UPDATE SET review_count = product_rating.review_count + 1,
                    rating_sum = product_rating.rating_sum + EXCLUDED.rating_sum""", productId, rating);
        products.evictAfterCommit(productId);
    }
}
