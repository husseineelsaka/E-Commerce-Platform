package com.team1.ecommerce.product.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class ProductAudienceValidatorTest {
    @Test
    void tokenWithoutProductAudienceFailsValidation() {
        Jwt token = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject("caller")
                .audience(java.util.List.of("order-service"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        assertThat(new ProductAudienceValidator().validate(token).hasErrors()).isTrue();
    }

    @Test
    void tokenWithNoAudienceFailsValidation() {
        Jwt token = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject("caller")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        assertThat(new ProductAudienceValidator().validate(token).hasErrors()).isTrue();
    }
}
