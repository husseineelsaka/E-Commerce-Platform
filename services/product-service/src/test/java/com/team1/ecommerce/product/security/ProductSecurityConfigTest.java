package com.team1.ecommerce.product.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class ProductSecurityConfigTest {
    @Test
    void nonGatewayIdentityHeaderGrantsNoRole() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Roles", "ADMIN");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            Jwt token = Jwt.withTokenValue("order-service")
                    .header("alg", "none")
                    .subject("order-service")
                    .audience(List.of("product-service"))
                    .claim("azp", "order-service")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(60))
                    .build();

            assertThat(new ProductSecurityConfig().authentication(token).getAuthorities()).isEmpty();
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }
}
