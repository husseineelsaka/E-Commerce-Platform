package com.team1.ecommerce.review.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team1.ecommerce.review.dto.ReviewPage;
import com.team1.ecommerce.review.dto.ReviewRequest;
import com.team1.ecommerce.review.dto.ReviewView;
import com.team1.ecommerce.review.exception.ReviewException;
import com.team1.ecommerce.review.exception.ReviewExceptionHandler;
import com.team1.ecommerce.review.security.ReviewSecurityConfig;
import com.team1.ecommerce.review.service.ReviewService;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(ReviewController.class)
@Import({ReviewSecurityConfig.class, ReviewExceptionHandler.class})
@ActiveProfiles("test")
class ReviewControllerTest {
    private static final String BODY = "{\"rating\": 5, \"text\": \"Great\"}";

    @Autowired MockMvc mvc;
    @MockitoBean ReviewService service;
    @MockitoBean JwtDecoder decoder;

    /** The bearer value names the calling client, so each test picks its caller (Keycloak is a system boundary). */
    @BeforeEach
    void token() {
        when(decoder.decode(anyString())).thenAnswer(call -> {
            String client = call.getArgument(0);
            return Jwt.withTokenValue(client).header("alg", "none").subject(client)
                    .audience(List.of("review-service")).claim("azp", client)
                    .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        });
    }

    @Test
    void customerSubmitsReviewAsGatewayIdentity() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.submit(eq(7L), eq("customer-1"), any(ReviewRequest.class)))
                .thenReturn(new ReviewView(id, 7L, 5, "Great", Instant.now()));

        mvc.perform(submit("gateway-service", "CUSTOMER", BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/products/7/reviews/" + id))
                .andExpect(jsonPath("$.reviewId").value(id.toString()))
                .andExpect(jsonPath("$.customerId").doesNotExist());
    }

    @Test
    void ratingOutsideOneToFiveIsBadRequest() throws Exception {
        mvc.perform(submit("gateway-service", "CUSTOMER", "{\"rating\": 6, \"text\": \"Great\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("rating must be between 1 and 5"));
        verifyNoInteractions(service);
    }

    @Test
    void secondReviewIsConflict() throws Exception {
        when(service.submit(eq(7L), eq("customer-1"), any(ReviewRequest.class)))
                .thenThrow(new ReviewException(HttpStatus.CONFLICT, "You have already reviewed this product"));

        mvc.perform(submit("gateway-service", "CUSTOMER", BODY)).andExpect(status().isConflict());
    }

    @Test
    void submitWithoutTokenIsUnauthorized() throws Exception {
        mvc.perform(post("/api/v1/products/7/reviews").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void submitWithoutCustomerRoleIsForbidden() throws Exception {
        mvc.perform(submit("gateway-service", "ADMIN", BODY)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void submitNotFromGatewayIsForbidden() throws Exception {
        mvc.perform(submit("order-service", "CUSTOMER", BODY)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void anonymousReadThroughGatewayReturnsPage() throws Exception {
        when(service.list(7L, 0, 10)).thenReturn(new ReviewPage(
                List.of(new ReviewView(UUID.randomUUID(), 7L, 4, "Good", Instant.now())),
                new ReviewPage.PageMetadata(10, 0, 1, 1)));

        mvc.perform(get("/api/v1/products/7/reviews?page=0&size=10").header("Authorization", "Bearer gateway-service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].rating").value(4))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void readNotFromGatewayIsForbidden() throws Exception {
        mvc.perform(get("/api/v1/products/7/reviews").header("Authorization", "Bearer payment-operator"))
                .andExpect(status().isForbidden());
    }

    private MockHttpServletRequestBuilder submit(String client, String roles, String body) {
        return post("/api/v1/products/7/reviews").header("Authorization", "Bearer " + client)
                .header("X-User-Id", "customer-1").header("X-User-Roles", roles)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
