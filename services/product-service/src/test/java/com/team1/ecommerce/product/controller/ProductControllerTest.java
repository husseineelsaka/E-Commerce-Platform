package com.team1.ecommerce.product.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team1.ecommerce.product.dto.ProductView;
import com.team1.ecommerce.product.entity.Category;
import com.team1.ecommerce.product.entity.Product;
import com.team1.ecommerce.product.exception.ProductExceptionHandler;
import com.team1.ecommerce.product.repository.ProductRepository;
import com.team1.ecommerce.product.repository.CategoryRepository;
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
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;

@WebMvcTest(ProductController.class)
@Import({ProductService.class, ProductSecurityConfig.class, ProductExceptionHandler.class})
@ActiveProfiles("test")
class ProductControllerTest {
    @Autowired
    MockMvc mvc;

    @MockitoBean
    ProductRepository products;

    @MockitoBean
    CategoryRepository categories;

    @MockitoBean
    JwtDecoder decoder;

    @BeforeEach
    void catalogue() throws Exception {
        ProductView view = new ProductView(1L, "Wireless Headphones", new BigDecimal("79.99"), 1L, "Electronics");
        when(products.browse(any())).thenReturn(new PageImpl<>(List.of(view), PageRequest.of(0, 20), 1));
        when(products.findViewById(1L)).thenReturn(Optional.of(view));
        when(products.findViewById(999L)).thenReturn(Optional.empty());
        when(decoder.decode(anyString())).thenAnswer(invocation -> token(invocation.getArgument(0)));
        Category category = new Category("Electronics");
        ReflectionTestUtils.setField(category, "id", 1L);
        when(categories.findById(1L)).thenReturn(Optional.of(category));
        when(products.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            ReflectionTestUtils.setField(product, "id", 42L);
            return product;
        });
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

    @Test
    void customerCannotCreateProduct() throws Exception {
        mvc.perform(post("/api/v1/products").header("Authorization", "Bearer gateway-service")
                        .header("X-User-Roles", "CUSTOMER").contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCreateReturnsLocationAndView() throws Exception {
        mvc.perform(post("/api/v1/products").header("Authorization", "Bearer gateway-service")
                        .header("X-User-Roles", "ADMIN").contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/products/42"))
                .andExpect(jsonPath("$.categoryName").value("Electronics"));
    }

    @Test
    void invalidBodyReturnsShaped400() throws Exception {
        mvc.perform(post("/api/v1/products").header("Authorization", "Bearer gateway-service")
                        .header("X-User-Roles", "ADMIN").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \" ,\"price\":0,\"categoryId\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/v1/products"));
    }

    @Test
    void updateUnknownProductReturns404() throws Exception {
        mvc.perform(put("/api/v1/products/999").header("Authorization", "Bearer gateway-service")
                        .header("X-User-Roles", "ADMIN").contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminDeleteReturns204() throws Exception {
        when(products.findById(1L)).thenReturn(Optional.of(new Product(new Category("Electronics"), "Desk Lamp", new BigDecimal("34.00"))));
        mvc.perform(delete("/api/v1/products/1").header("Authorization", "Bearer gateway-service")
                        .header("X-User-Roles", "ADMIN"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteUnknownProductReturns404() throws Exception {
        mvc.perform(delete("/api/v1/products/999").header("Authorization", "Bearer gateway-service")
                        .header("X-User-Roles", "ADMIN"))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonGatewayAdminHeaderCannotCreateProduct() throws Exception {
        mvc.perform(post("/api/v1/products").header("Authorization", "Bearer order-service")
                        .header("X-User-Roles", "ADMIN").contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingTokenCannotCreateProduct() throws Exception {
        mvc.perform(post("/api/v1/products").header("X-User-Roles", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void excessivePriceScaleReturns400() throws Exception {
        mvc.perform(post("/api/v1/products").header("Authorization", "Bearer gateway-service")
                        .header("X-User-Roles", "ADMIN").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Desk Lamp\",\"price\":34.001,\"categoryId\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void unknownCategoryReturnsShaped400() throws Exception {
        mvc.perform(post("/api/v1/products").header("Authorization", "Bearer gateway-service")
                        .header("X-User-Roles", "ADMIN").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Desk Lamp\",\"price\":34.00,\"categoryId\":999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Category 999 was not found"))
                .andExpect(jsonPath("$.path").value("/api/v1/products"));
    }

    private String validBody() {
        return "{\"name\":\"Desk Lamp\",\"price\":34.00,\"categoryId\":1}";
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
