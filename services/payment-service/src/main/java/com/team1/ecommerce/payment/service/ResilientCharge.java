package com.team1.ecommerce.payment.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The simulated charge behind Resilience4j Retry and CircuitBreaker (handbook "+ Payment path" default, ADD §6 F3).
 * A declined attempt is retried; when retries are exhausted or the circuit is open the charge counts as failed.
 */
@Component
public class ResilientCharge {
    private static final Logger log = LoggerFactory.getLogger(ResilientCharge.class);

    private final ChargeSimulator simulator;

    public ResilientCharge(ChargeSimulator simulator) {
        this.simulator = simulator;
    }

    @Retry(name = "charge", fallbackMethod = "failed")
    @CircuitBreaker(name = "charge")
    public boolean charge() {
        if (!simulator.charge()) {
            throw new ChargeDeclinedException();
        }
        return true;
    }

    @SuppressWarnings("unused")
    private boolean failed(Exception exception) {
        log.warn("Charge failed after retries: {}", exception.toString());
        return false;
    }

    static class ChargeDeclinedException extends RuntimeException {
        ChargeDeclinedException() {
            super("Simulated charge declined");
        }
    }
}
