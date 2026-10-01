package com.team1.ecommerce.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team1.ecommerce.order.dto.OrderRequest;
import com.team1.ecommerce.order.exception.OrderException;
import com.team1.ecommerce.order.repository.OrderRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Placing an order against stubbed Product and Inventory HTTP services (the system boundary), with the real
 * Feign clients and Resilience4j settings. Persistence is stubbed here and covered by OrderServiceIT.
 */
@SpringBootTest(properties = "spring.autoconfigure.exclude="
        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration")
@ActiveProfiles("test")
class OrderServiceTest {
    static final MockWebServer product = new MockWebServer();
    static final MockWebServer inventory = new MockWebServer();

    @DynamicPropertySource
    static void clients(DynamicPropertyRegistry registry) {
        registry.add("order.clients.product-url", () -> product.url("/").toString());
        registry.add("order.clients.inventory-url", () -> inventory.url("/").toString());
    }

    @BeforeAll static void start() throws Exception { product.start(); inventory.start(); }
    @AfterAll static void stop() throws Exception { product.shutdown(); inventory.shutdown(); }

    @Autowired OrderService service;
    @MockitoBean OrderWriter writer;
    @MockitoBean OrderRepository orders;
    /** Keycloak's token endpoint is the boundary; the Feign interceptor gets a fixed token. */
    @MockitoBean OAuth2AuthorizedClientManager manager;

    @BeforeEach
    void token() {
        var registration = ClientRegistration.withRegistrationId("order-service").clientId("order-service")
                .clientSecret("test").tokenUri("http://localhost:1/unused")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS).build();
        var token = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "test-token",
                Instant.now(), Instant.now().plusSeconds(60));
        when(manager.authorize(any())).thenReturn(new OAuth2AuthorizedClient(registration, "order-service", token));
    }

    @Test
    void inventoryDownRejectsQuickly() {
        respond(product, () -> json("{\"price\":12.00}"));
        respond(inventory, () -> json("{\"available\":true}").setBodyDelay(3, TimeUnit.SECONDS));
        long start = System.nanoTime();

        assertThatThrownBy(() -> service.place("owner", request()))
                .isInstanceOf(OrderException.class).extracting("status").isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        // TimeLimiter 500 ms × 2 attempts, far below the 3 s the stub would take.
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
        verify(writer, never()).save(any(), any());
    }

    @Test
    void outOfStockSavesNothing() {
        respond(product, () -> json("{\"price\":12.00}"));
        respond(inventory, () -> json("{\"available\":false}"));

        assertThatThrownBy(() -> service.place("owner", request()))
                .isInstanceOf(OrderException.class).extracting("status").isEqualTo(HttpStatus.CONFLICT);
        verify(writer, never()).save(any(), any());
    }

    @Test
    void unknownProductIsRejectedWithoutRetry() {
        AtomicInteger productCalls = new AtomicInteger();
        respond(product, () -> { productCalls.incrementAndGet(); return new MockResponse().setResponseCode(404); });

        assertThatThrownBy(() -> service.place("owner", request()))
                .isInstanceOf(OrderException.class).extracting("status").isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(productCalls).hasValue(1);
        verify(writer, never()).save(any(), any());
    }

    @Test
    void availableStockIsSavedWithTheProductPrice() {
        respond(product, () -> json("{\"price\":12.50}"));
        respond(inventory, () -> json("{\"available\":true}"));

        service.place("owner", new OrderRequest(List.of(new OrderRequest.Item(1L, 2))));

        verify(writer).save("owner", List.of(new OrderWriter.PricedItem(1L, 2, new java.math.BigDecimal("12.50"))));
    }

    private static void respond(MockWebServer server, Supplier<MockResponse> response) {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return response.get();
            }
        });
    }

    private static MockResponse json(String body) {
        return new MockResponse().setBody(body).addHeader("Content-Type", "application/json");
    }

    private static OrderRequest request() {
        return new OrderRequest(List.of(new OrderRequest.Item(1L, 1)));
    }
}
