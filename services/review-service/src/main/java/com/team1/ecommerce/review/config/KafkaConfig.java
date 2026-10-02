package com.team1.ecommerce.review.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class KafkaConfig {
    /** Review traffic stays off the Saga topics; Product subscribes only to this one (ADD §3.3). */
    @Bean
    NewTopic reviewEvents() {
        return TopicBuilder.name("review-events").partitions(3).replicas(1).build();
    }
}
