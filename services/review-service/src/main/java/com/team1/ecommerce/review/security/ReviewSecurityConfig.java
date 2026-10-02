package com.team1.ecommerce.review.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;

@Configuration
public class ReviewSecurityConfig {
    /**
     * Validates the token's {@code iss} against the public issuer (what clients see) but loads the signing keys
     * from {@code jwk-set-uri}, which can be Keycloak's internal address in Compose or Kubernetes. No Keycloak call
     * happens at startup: the key set is fetched on the first request.
     */
    @Bean
    JwtDecoder jwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
                          @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri), new ReviewAudienceValidator()));
        return decoder;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper mapper) throws Exception {
        return http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/products/*/reviews").access(customerGateway())
                .requestMatchers(HttpMethod.GET, "/api/v1/products/*/reviews").access(gateway())
                .anyRequest().denyAll())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) ->
                    writeError(mapper, request, response, HttpStatus.UNAUTHORIZED))
                .accessDeniedHandler((request, response, exception) ->
                    writeError(mapper, request, response, HttpStatus.FORBIDDEN)))
            .csrf(AbstractHttpConfigurer::disable)
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(this::authentication)))
            .build();
    }

    private void writeError(ObjectMapper mapper, HttpServletRequest request,
                            HttpServletResponse response, HttpStatus status) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        mapper.writeValue(response.getWriter(), Map.of(
                "status", status.value(), "error", status.getReasonPhrase(),
                "message", status.getReasonPhrase(), "path", request.getRequestURI()));
    }

    /** Public reads still arrive only through the Gateway, which presents its own client token (ADD §7). */
    private AuthorizationManager<RequestAuthorizationContext> gateway() {
        return (authentication, context) -> new AuthorizationDecision(
                authentication.get() instanceof JwtAuthenticationToken jwt
                        && "gateway-service".equals(jwt.getToken().getClaimAsString("azp")));
    }

    private AuthorizationManager<RequestAuthorizationContext> customerGateway() {
        return (authentication, context) -> {
            Authentication caller = authentication.get();
            if (!(caller instanceof JwtAuthenticationToken jwt)) {
                return new AuthorizationDecision(false);
            }
            return new AuthorizationDecision("gateway-service".equals(jwt.getToken().getClaimAsString("azp"))
                    && context.getRequest().getHeader("X-User-Id") != null
                    && !context.getRequest().getHeader("X-User-Id").isBlank()
                    && caller.getAuthorities().stream().anyMatch(role -> "ROLE_CUSTOMER".equals(role.getAuthority())));
        };
    }

    AbstractAuthenticationToken authentication(Jwt jwt) {
        Collection<GrantedAuthority> roles = List.of();
        if ("gateway-service".equals(jwt.getClaimAsString("azp"))) {
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
            String roleHeader = request.getHeader("X-User-Roles");
            if (roleHeader != null) {
                roles = Arrays.stream(roleHeader.split(","))
                        .map(String::trim).filter(role -> !role.isEmpty())
                        .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                        .toList();
            }
        }
        return new JwtAuthenticationToken(jwt, roles);
    }
}

