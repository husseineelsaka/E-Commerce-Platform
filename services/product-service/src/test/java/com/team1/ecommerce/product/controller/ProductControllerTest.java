package com.team1.ecommerce.product.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team1.ecommerce.product.dto.ProductView;
import com.team1.ecommerce.product.exception.ProductExceptionHandler;
import com.team1.ecommerce.product.repository.ProductRepository;
import com.team1.ecommerce.product.security.ProductSecurityConfig;
import com.team1.ecommerce.product.service.ProductService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductController.class)
@Import({ProductService.class, ProductSecurityConfig.class, ProductExceptionHandler.class})
@ActiveProfiles("test")
class ProductControllerTest {
    @Autowired
    MockMvc mvc;

    @MockitoBean
    ProductRepository products;

    @MockitoBean
    JwtDecoder decoder;

    @BeforeEach
    void catalogue() throws Exception {
        ProductView view = new ProductView(1L, "Wireless Headphones", new BigDecimal("79.99"), 1L, "Electronics");
        when(products.browse(any())).thenReturn(new PageImpl<>(List.of(view), PageRequest.of(0, 20), 1));
        when(products.findViewById(1L)).thenReturn(Optional.of(view));
        when(products.findViewById(999L)).thenReturn(Optional.empty());
        when(decoder.decode(anyString())).thenAnswer(invocation -> token(invocation.getArgument(0)));
    }

    @Test
    void listIncludesCategoryNameInStablePage() throws Exception {
        mvc.perform(get("/api/v1/products").header("Authorization", "Bearer gateway-service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].categoryName").value("Electronics"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void unknownProductReturnsShaped404() throws Exception {
        mvc.perform(get("/api/v1/products/999").header("Authorization", "Bearer gateway-service"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/api/v1/products/999"));
    }

    @Test
    void sizeAboveHundredReturns400() throws Exception {
        mvc.perform(get("/api/v1/products?size=101").header("Authorization", "Bearer gateway-service"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void missingTokenReturns401() throws Exception {
        mvc.perform(get("/api/v1/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.path").value("/api/v1/products"));
    }

    @Test
    void gatewayTokenCanBrowse() throws Exception {
        mvc.perform(get("/api/v1/products").header("Authorization", "Bearer gateway-service"))
                .andExpect(status().isOk());
    }

    @Test
    void orderTokenCanReadDetail() throws Exception {
        mvc.perform(get("/api/v1/products/1").header("Authorization", "Bearer order-service"))
                .andExpect(status().isOk());
    }

    @Test
    void orderTokenCannotBrowse() throws Exception {
        mvc.perform(get("/api/v1/products").header("Authorization", "Bearer order-service"))
                .andExpect(status().isForbidden());
    }

    @Test
    void paymentOperatorCannotReadProduct() throws Exception {
        mvc.perform(get("/api/v1/products/1").header("Authorization", "Bearer payment-operator"))
                .andExpect(status().isForbidden());
    }

    private Jwt token(String client) {
        return Jwt.withTokenValue(client)
                .header("alg", "none")
                .subject(client)
                .audience(List.of("product-service"))
                .claim("azp", client)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }

}
