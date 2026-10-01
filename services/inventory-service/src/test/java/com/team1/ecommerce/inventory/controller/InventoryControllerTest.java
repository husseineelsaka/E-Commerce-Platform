package com.team1.ecommerce.inventory.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team1.ecommerce.inventory.dto.StockView;
import com.team1.ecommerce.inventory.exception.StockNotFoundException;
import com.team1.ecommerce.inventory.security.InventorySecurityConfig;
import com.team1.ecommerce.inventory.service.InventoryService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InventoryController.class)
@Import(InventorySecurityConfig.class)
@ActiveProfiles("test")
class InventoryControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean InventoryService inventory;
    @MockitoBean JwtDecoder decoder;

    @BeforeEach
    void tokens() {
        when(decoder.decode(org.mockito.ArgumentMatchers.anyString())).thenAnswer(call -> token(call.getArgument(0)));
    }

    @Test void orderCanCheckAvailableStock() throws Exception {
        when(inventory.available(1L, 2)).thenReturn(true);
        mvc.perform(get("/api/v1/inventory/check?productId=1&quantity=2").header("Authorization", "Bearer order-service"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(true));
    }

    @Test void orderCanCheckInsufficientStock() throws Exception {
        mvc.perform(get("/api/v1/inventory/check?productId=1&quantity=99").header("Authorization", "Bearer order-service"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
    }

    @Test void gatewayCannotCheckStock() throws Exception {
        mvc.perform(get("/api/v1/inventory/check?productId=1&quantity=2").header("Authorization", "Bearer gateway-service"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
    }

    @Test void adminCanViewStock() throws Exception {
        when(inventory.find(1L)).thenReturn(new StockView(1L, 5, 1));
        mvc.perform(get("/api/v1/inventory/1").header("Authorization", "Bearer gateway-service")
                        .header("X-User-Roles", "ADMIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reserved").value(1));
    }

    @Test void adminCanAdjustStock() throws Exception {
        when(inventory.adjust(1L, 7)).thenReturn(new StockView(1L, 7, 1));
        mvc.perform(put("/api/v1/inventory/1").header("Authorization", "Bearer gateway-service")
                        .header("X-User-Roles", "ADMIN").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"available\":7}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(7));
    }

    @Test void customerCannotViewStock() throws Exception {
        mvc.perform(get("/api/v1/inventory/1").header("Authorization", "Bearer gateway-service")
                        .header("X-User-Roles", "CUSTOMER"))
                .andExpect(status().isForbidden());
    }

    @Test void orderCannotViewStockEvenWithForgedAdminHeader() throws Exception {
        mvc.perform(get("/api/v1/inventory/1").header("Authorization", "Bearer order-service")
                        .header("X-User-Roles", "ADMIN"))
                .andExpect(status().isForbidden());
    }

    @Test void orderCannotAdjustStock() throws Exception {
        mvc.perform(put("/api/v1/inventory/1").header("Authorization", "Bearer order-service")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"available\":7}"))
                .andExpect(status().isForbidden());
    }

    @Test void zeroQuantityIsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/inventory/check?productId=1&quantity=0").header("Authorization", "Bearer order-service"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.path").value("/api/v1/inventory/check"));
    }

    @Test void missingQuantityIsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/inventory/check?productId=1").header("Authorization", "Bearer order-service"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    @Test void invalidAdjustmentIsBadRequest() throws Exception {
        mvc.perform(put("/api/v1/inventory/1").header("Authorization", "Bearer gateway-service")
                        .header("X-User-Roles", "ADMIN").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"available\":-1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    @Test void unknownStockReturns404() throws Exception {
        when(inventory.find(999L)).thenThrow(new StockNotFoundException(999L));
        mvc.perform(get("/api/v1/inventory/999").header("Authorization", "Bearer gateway-service")
                        .header("X-User-Roles", "ADMIN"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
    }

    private Jwt token(String client) {
        return Jwt.withTokenValue(client).header("alg", "none").subject(client)
                .audience(List.of("inventory-service")).claim("azp", client)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }
}
