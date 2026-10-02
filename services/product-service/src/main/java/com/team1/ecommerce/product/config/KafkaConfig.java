package com.team1.ecommerce.product.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {
    /**
     * Declared here as well as in review-service: if Product starts first, its listener would otherwise subscribe
     * to a missing topic and only see it after the next metadata refresh (5 minutes by default).
     */
    @Bean
    NewTopic reviewEvents() {
        return TopicBuilder.name("review-events").partitions(3).replicas(1).build();
    }

    /** A record that still fails after 3 retries one second apart is parked on {@code review-events.DLT} (NFR-10). */
    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafka) {
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(kafka), new FixedBackOff(1000, 3));
    }
}
