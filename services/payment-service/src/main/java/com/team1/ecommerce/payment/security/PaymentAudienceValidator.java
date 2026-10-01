package com.team1.ecommerce.payment.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public class PaymentAudienceValidator implements OAuth2TokenValidator<Jwt> {
    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (token.getAudience() != null && token.getAudience().contains("payment-service")) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Token audience must include payment-service", null));
    }
}
