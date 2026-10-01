package com.team1.ecommerce.inventory.repository;

import java.util.stream.LongStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team1.ecommerce.inventory.InventoryServiceApplication;
import com.team1.ecommerce.inventory.service.InventoryService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = InventoryServiceApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class InventoryRepositoryIT {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired InventoryRepository stocks;
    @Autowired InventoryService inventory;
    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder decoder;

    @Test void flywaySeedsCatalogueIncludingOutOfStockProduct() {
        assertThat(stocks.findAllById(LongStream.rangeClosed(1, 20).boxed().toList())).hasSize(20);
        assertThat(stocks.findById(20L)).get().extracting("available").isEqualTo(0);
    }

    @Test void checkUsesRealStockRows() {
        assertThat(inventory.available(1L, 2)).isTrue();
        assertThat(inventory.available(20L, 1)).isFalse();
        assertThat(inventory.available(999L, 1)).isFalse();
    }

    @Test void putCreatesNewStockRow() throws Exception {
        org.mockito.Mockito.when(decoder.decode("gateway-service")).thenReturn(gateway());
        mvc.perform(put("/api/v1/inventory/1000").header("Authorization", "Bearer gateway-service")
                        .header("X-User-Roles", "ADMIN").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"available\":4}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reserved").value(0));
        assertThat(stocks.findById(1000L)).get().extracting("available").isEqualTo(4);
    }

    @Test void putUpdatesExistingRowWithoutChangingReserved() throws Exception {
        org.mockito.Mockito.when(decoder.decode("gateway-service")).thenReturn(gateway());
        mvc.perform(put("/api/v1/inventory/1").header("Authorization", "Bearer gateway-service")
                        .header("X-User-Roles", "ADMIN").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"available\":8}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(8));
        assertThat(stocks.findById(1L)).get().extracting("available").isEqualTo(8);
    }

    private Jwt gateway() {
        return Jwt.withTokenValue("gateway-service").header("alg", "none").subject("gateway-service")
                .audience(List.of("inventory-service")).claim("azp", "gateway-service")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }
}
