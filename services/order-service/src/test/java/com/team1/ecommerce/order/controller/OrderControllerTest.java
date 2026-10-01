package com.team1.ecommerce.order.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team1.ecommerce.order.dto.OrderPage;
import com.team1.ecommerce.order.dto.OrderView;
import com.team1.ecommerce.order.exception.OrderException;
import com.team1.ecommerce.order.exception.OrderExceptionHandler;
import com.team1.ecommerce.order.security.OrderSecurityConfig;
import com.team1.ecommerce.order.service.OrderService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrderController.class)
@Import({OrderSecurityConfig.class, OrderExceptionHandler.class})
@ActiveProfiles("test")
class OrderControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean OrderService service;
    @MockitoBean JwtDecoder decoder;
    UUID id = UUID.randomUUID();

    @BeforeEach
    void token() {
        when(decoder.decode(org.mockito.ArgumentMatchers.anyString())).thenAnswer(call -> {
            String client = call.getArgument(0);
            return Jwt.withTokenValue(client).header("alg", "none").subject(client)
                    .audience(List.of("order-service")).claim("azp", client)
                    .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        });
    }

    @Test
    void cannotReadOtherCustomersOrder() throws Exception {
        when(service.get(id, "other")).thenThrow(new OrderException(HttpStatus.NOT_FOUND, "Order not found"));
        mvc.perform(get("/api/v1/orders/{id}", id).header("Authorization", "Bearer gateway-service")
                        .header("X-User-Id", "other").header("X-User-Roles", "CUSTOMER"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.path").value("/api/v1/orders/" + id));
    }

    @Test
    void listReturnsOnlyCallersOrders() throws Exception {
        var view = new OrderView(id, "PENDING", new BigDecimal("12.00"), Instant.now(), List.of());
        when(service.list("owner", 0, 20)).thenReturn(new OrderPage(List.of(view), new OrderPage.PageMetadata(20, 0, 1, 1)));
        mvc.perform(get("/api/v1/orders").header("Authorization", "Bearer gateway-service")
                        .header("X-User-Id", "owner").header("X-User-Roles", "CUSTOMER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].orderId").value(id.toString()));
    }

    @Test
    void nonGatewayCallerForbidden() throws Exception {
        mvc.perform(get("/api/v1/orders").header("Authorization", "Bearer payment-operator")
                        .header("X-User-Id", "owner").header("X-User-Roles", "CUSTOMER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerRoleRequired() throws Exception {
        mvc.perform(get("/api/v1/orders").header("Authorization", "Bearer gateway-service")
                        .header("X-User-Id", "owner").header("X-User-Roles", "ADMIN"))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidBodyReturns400() throws Exception {
        mvc.perform(post("/api/v1/orders").header("Authorization", "Bearer gateway-service")
                        .header("X-User-Id", "owner").header("X-User-Roles", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }
}
