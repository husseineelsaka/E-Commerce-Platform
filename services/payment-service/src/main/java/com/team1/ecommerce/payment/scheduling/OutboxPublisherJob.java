package com.team1.ecommerce.payment.scheduling;

import com.team1.ecommerce.payment.service.OutboxRelay;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Polls the outbox every 500 ms (ADD §5). Switched off in tests that do not run Kafka. */
@Component
@ConditionalOnProperty(name = "outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisherJob {
    private final OutboxRelay relay;

    public OutboxPublisherJob(OutboxRelay relay) {
        this.relay = relay;
    }

    @Scheduled(fixedDelayString = "${outbox.publisher.poll-interval:500}")
    public void publish() {
        relay.publishPending();
    }
}
