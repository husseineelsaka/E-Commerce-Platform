package com.team1.ecommerce.order.client;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

@Configuration
public class ClientConfig {
    /**
     * Client Credentials manager that works outside an HTTP request: the Feign calls run on
     * Resilience4j/CompletableFuture threads, where the default request-bound manager cannot be used.
     */
    @Bean
    OAuth2AuthorizedClientManager authorizedClientManager(ClientRegistrationRepository registrations,
                                                          OAuth2AuthorizedClientService clients) {
        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, clients);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());
        return manager;
    }

    @Bean
    RequestInterceptor clientCredentials(OAuth2AuthorizedClientManager manager) {
        return template -> {
            var client = manager.authorize(OAuth2AuthorizeRequest.withClientRegistrationId("order-service")
                    .principal("order-service").build());
            if (client == null) throw new IllegalStateException("Order client credentials unavailable");
            template.header("Authorization", "Bearer " + client.getAccessToken().getTokenValue());
        };
    }
}
