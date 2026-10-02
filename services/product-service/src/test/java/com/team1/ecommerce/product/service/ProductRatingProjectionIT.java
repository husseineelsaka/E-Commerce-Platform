package com.team1.ecommerce.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.team1.ecommerce.product.ProductServiceApplication;
import com.team1.ecommerce.product.dto.ProductView;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

/** The ReviewSubmitted projection on real PostgreSQL, Redis, and Kafka (ADD §6 F1, §2). */
@SpringBootTest(classes = ProductServiceApplication.class, properties = {"spring.kafka.listener.auto-startup=true",
        "spring.kafka.admin.auto-create=true"})
@ActiveProfiles("test")
@Testcontainers
class ProductRatingProjectionIT {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.9.1");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.4.5-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired ProductRatingProjection projection;
    @Autowired ProductService products;
    @Autowired JdbcTemplate jdbc;
    @Autowired KafkaTemplate<String, String> producer;
    @Autowired StringRedisTemplate redisKeys;

    @BeforeEach
    void clean() {
        jdbc.execute("DELETE FROM product_rating; DELETE FROM processed_event");
        redisKeys.delete(redisKeys.keys("products*"));
    }

    @Test
    void reviewSubmittedFromKafkaUpdatesCachedProductDetail() {
        assertThat(products.findById(2L).reviewCount()).isZero();

        producer.send("review-events", UUID.randomUUID().toString(), reviewSubmitted(UUID.randomUUID(), 2L, 4));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            ProductView view = products.findById(2L);
            assertThat(view.reviewCount()).isEqualTo(1);
            assertThat(view.averageRating()).isEqualByComparingTo("4.00");
        });
    }

    @Test
    void averageIsRatingSumOverCount() {
        projection.apply(UUID.randomUUID(), 3L, 4);
        projection.apply(UUID.randomUUID(), 3L, 5);

        ProductView view = products.findById(3L);
        assertThat(view.reviewCount()).isEqualTo(2);
        assertThat(view.averageRating()).isEqualByComparingTo("4.50");
    }

    @Test
    void duplicateEventCountedOnce() {
        UUID eventId = UUID.randomUUID();

        projection.apply(eventId, 4L, 5);
        projection.apply(eventId, 4L, 5);

        assertThat(jdbc.queryForObject("SELECT review_count FROM product_rating WHERE product_id = 4", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT rating_sum FROM product_rating WHERE product_id = 4", Long.class)).isEqualTo(5L);
    }

    @Test
    void unknownProductIsSkipped() {
        UUID eventId = UUID.randomUUID();

        projection.apply(eventId, 999_999L, 3);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_event WHERE event_id = ?", Integer.class, eventId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_rating", Integer.class)).isZero();
    }

    @Test
    void productWithoutReviewsHasNoAverage() {
        ProductView view = products.findById(5L);

        assertThat(view.averageRating()).isNull();
        assertThat(view.reviewCount()).isZero();
    }

    private static String reviewSubmitted(UUID eventId, long productId, int rating) {
        return """
                {"eventId":"%s","eventType":"ReviewSubmitted","version":1,"reviewId":"%s",
                 "productId":%d,"customerId":"customer-1","rating":%d}""".formatted(eventId, UUID.randomUUID(), productId, rating);
    }
}
