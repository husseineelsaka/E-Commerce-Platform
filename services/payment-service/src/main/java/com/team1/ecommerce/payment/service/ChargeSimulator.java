package com.team1.ecommerce.payment.service;

import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ChargeSimulator {
    private final double failureRate;

    public ChargeSimulator(@Value("${payment.simulation.failure-rate:0.0}") double failureRate) {
        if (failureRate < 0.0 || failureRate > 1.0 || Double.isNaN(failureRate)) {
            throw new IllegalArgumentException("payment.simulation.failure-rate must be between 0.0 and 1.0");
        }
        this.failureRate = failureRate;
    }

    public boolean charge() {
        return ThreadLocalRandom.current().nextDouble() >= failureRate;
    }
}
