package com.team1.ecommerce.inventory.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class InventoryAudienceValidatorTest {
    @Test void wrongAudienceIsRejected() {
        Jwt jwt = Jwt.withTokenValue("test").header("alg", "none")
                .audience(List.of("product-service")).issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60)).build();
        assertThat(new InventoryAudienceValidator().validate(jwt).hasErrors()).isTrue();
    }

    @Test void inventoryAudienceIsAccepted() {
        Jwt jwt = Jwt.withTokenValue("test").header("alg", "none")
                .audience(List.of("inventory-service")).issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60)).build();
        assertThat(new InventoryAudienceValidator().validate(jwt).hasErrors()).isFalse();
    }
}
