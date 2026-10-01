package com.team1.ecommerce.gateway.security;

import java.util.stream.Collectors;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
public class TrustedIdentityFilter implements GlobalFilter, Ordered {
    private final ReactiveOAuth2AuthorizedClientManager authorizedClients;

    public TrustedIdentityFilter(ReactiveOAuth2AuthorizedClientManager authorizedClients) {
        this.authorizedClients = authorizedClients;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var request = OAuth2AuthorizeRequest.withClientRegistrationId("gateway-service")
                .principal("gateway-service").build();
        return authorizedClients.authorize(request)
                .switchIfEmpty(Mono.error(new IllegalStateException("Gateway service token unavailable")))
                .flatMap(client -> exchange.getPrincipal()
                        .ofType(JwtAuthenticationToken.class)
                        .map(user -> withIdentity(exchange, user, client.getAccessToken().getTokenValue()))
                        .switchIfEmpty(Mono.fromSupplier(() ->
                                withIdentity(exchange, null, client.getAccessToken().getTokenValue()))))
                .flatMap(chain::filter);
    }

    private ServerWebExchange withIdentity(ServerWebExchange exchange, JwtAuthenticationToken user, String serviceToken) {
        var downstream = exchange.getRequest().mutate().headers(headers -> {
            headers.keySet().removeIf(name -> name.regionMatches(true, 0, "X-User-", 0, 7));
            headers.setBearerAuth(serviceToken);
            if (user != null) {
                headers.set("X-User-Id", user.getToken().getSubject());
                String roles = user.getAuthorities().stream().map(authority -> authority.getAuthority())
                        .filter(authority -> authority.startsWith("ROLE_"))
                        .map(authority -> authority.substring(5)).collect(Collectors.joining(","));
                headers.set("X-User-Roles", roles);
            }
        }).build();
        return exchange.mutate().request(downstream).build();
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
