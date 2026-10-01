package com.team1.ecommerce.gateway.security;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;

import reactor.core.publisher.Flux;

@Configuration
public class GatewaySecurityConfig {
    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        ReactiveJwtAuthenticationConverter authenticationConverter = new ReactiveJwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(this::realmAuthorities);

        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        // Actuator exists only on the management port (management.server.port), never on 8080.
                        .pathMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .pathMatchers("/api/v1/inventory/check").denyAll()
                        .pathMatchers(HttpMethod.GET, "/api/v1/products", "/api/v1/products/**").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/products").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.PUT, "/api/v1/products/{id}").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.DELETE, "/api/v1/products/{id}").hasRole("ADMIN")
                        .pathMatchers("/api/v1/orders", "/api/v1/orders/**").hasRole("CUSTOMER")
                        .pathMatchers(HttpMethod.GET, "/api/v1/inventory/{productId}").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.PUT, "/api/v1/inventory/{productId}").hasRole("ADMIN")
                        .anyExchange().denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(authenticationConverter)))
                .build();
    }

    /** Validates {@code iss} against the public issuer; signing keys come from {@code jwk-set-uri} (internal URL in containers). */
    @Bean
    ReactiveJwtDecoder jwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
                                  @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
        OAuth2TokenValidator<Jwt> authorizedParty = jwt -> "user-sign-in".equals(jwt.getClaimAsString("azp"))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Unexpected authorized party", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer), authorizedParty));
        return decoder;
    }

    @Bean
    ReactiveOAuth2AuthorizedClientManager authorizedClientManager(ReactiveClientRegistrationRepository registrations) {
        // ponytail: Cache is per gateway process; use shared authorized-client storage if process-local reuse becomes insufficient.
        var service = new InMemoryReactiveOAuth2AuthorizedClientService(registrations);
        var manager = new AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(registrations, service);
        manager.setAuthorizedClientProvider(ReactiveOAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials().build());
        return manager;
    }

    private Flux<GrantedAuthority> realmAuthorities(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof List<?> roles)) {
            return Flux.empty();
        }
        return Flux.fromIterable(roles).filter(String.class::isInstance)
                .filter(role -> "ADMIN".equals(role) || "CUSTOMER".equals(role))
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role));
    }
}
