package com.team1.ecommerce.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team1.ecommerce.review.dto.ReviewPage;
import com.team1.ecommerce.review.dto.ReviewRequest;
import com.team1.ecommerce.review.dto.ReviewView;
import com.team1.ecommerce.review.exception.ReviewException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

/** Review rules and the ReviewSubmitted outbox on real PostgreSQL, Flyway, and Kafka. */
@SpringBootTest(properties = {"outbox.publisher.enabled=true", "outbox.publisher.poll-interval=200"})
@ActiveProfiles("test")
@Testcontainers
class ReviewServiceIT {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.9");

    @Container
    @ServiceConnection
    static final KafkaContainer kafka = new KafkaContainer("apache/kafka:3.9.1");

    @Autowired ReviewService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @AfterEach
    void clean() {
        jdbc.execute("DELETE FROM outbox_event; DELETE FROM review");
    }

    @Test
    void submitPublishesReviewSubmittedToReviewEvents() throws Exception {
        ReviewView review = service.submit(7L, "customer-1", new ReviewRequest(4, "  Solid kettle.  "));

        assertThat(review.text()).isEqualTo("Solid kettle.");
        JsonNode event = mapper.readTree(readReviewEvent(review.reviewId().toString()).value());
        assertThat(event.path("eventType").asText()).isEqualTo("ReviewSubmitted");
        assertThat(event.path("version").asInt()).isEqualTo(1);
        assertThat(event.path("eventId").asText()).isNotBlank();
        assertThat(event.path("reviewId").asText()).isEqualTo(review.reviewId().toString());
        assertThat(event.path("productId").asLong()).isEqualTo(7L);
        assertThat(event.path("customerId").asText()).isEqualTo("customer-1");
        assertThat(event.path("rating").asInt()).isEqualTo(4);
    }

    @Test
    void secondReviewOfSameProductIsConflictAndWritesNoEvent() {
        service.submit(7L, "customer-1", new ReviewRequest(4, "First"));

        assertThatThrownBy(() -> service.submit(7L, "customer-1", new ReviewRequest(2, "Second")))
                .isInstanceOfSatisfying(ReviewException.class, ex -> assertThat(ex.status()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM review", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event", Integer.class)).isEqualTo(1);
    }

    @Test
    void sameCustomerMayReviewDifferentProducts() {
        service.submit(7L, "customer-1", new ReviewRequest(4, "Kettle"));
        service.submit(8L, "customer-1", new ReviewRequest(5, "Toaster"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM review", Integer.class)).isEqualTo(2);
    }

    @Test
    void listReturnsOneProductsReviewsNewestFirst() {
        service.submit(7L, "customer-1", new ReviewRequest(3, "Older"));
        service.submit(7L, "customer-2", new ReviewRequest(5, "Newer"));
        service.submit(8L, "customer-1", new ReviewRequest(1, "Other product"));

        ReviewPage page = service.list(7L, 0, 10);

        assertThat(page.content()).extracting(ReviewView::text).containsExactly("Newer", "Older");
        assertThat(page.page().totalElements()).isEqualTo(2);
    }

    @Test
    void invalidPagingIsBadRequest() {
        assertThatThrownBy(() -> service.list(7L, 0, 101))
                .isInstanceOfSatisfying(ReviewException.class, ex -> assertThat(ex.status()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private ConsumerRecord<String, String> readReviewEvent(String key) {
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "review-it-" + key,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
                new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of("review-events"));
            AtomicReference<ConsumerRecord<String, String>> found = new AtomicReference<>();
            await().atMost(Duration.ofSeconds(20)).until(() -> {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (key.equals(record.key())) {
                        found.set(record);
                    }
                }
                return found.get() != null;
            });
            return found.get();
        }
    }
}
