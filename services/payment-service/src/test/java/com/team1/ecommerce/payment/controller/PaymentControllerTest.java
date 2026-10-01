package com.team1.ecommerce.payment.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team1.ecommerce.payment.dto.PaymentResponse;
import com.team1.ecommerce.payment.exception.PaymentExceptionHandler;
import com.team1.ecommerce.payment.security.PaymentSecurityConfig;
import com.team1.ecommerce.payment.service.PaymentConflictException;
import com.team1.ecommerce.payment.service.PaymentMismatchException;
import com.team1.ecommerce.payment.service.PaymentNotFoundException;
import com.team1.ecommerce.payment.service.PaymentService;
import com.team1.ecommerce.payment.service.PaymentService.CreatedPayment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** HTTP contract and caller rules of the payment API; the service is stubbed, persistence is covered by PaymentServiceIT. */
@WebMvcTest(PaymentController.class)
@Import({PaymentSecurityConfig.class, PaymentExceptionHandler.class})
@ActiveProfiles("test")
class PaymentControllerTest {
    private static final UUID ORDER = UUID.fromString("7d3c4a52-2f9f-4a0e-9b71-0d3f1b6f4a11");
    private static final UUID PAYMENT = UUID.fromString("0b6f9c8e-6c1d-4a63-8a3e-5f0f2d9e7c44");
    private static final String BODY = "{\"orderId\":\"" + ORDER + "\",\"amount\":49.90}";
    private static final PaymentResponse COMPLETED = new PaymentResponse(PAYMENT, ORDER, new BigDecimal("49.90"), "COMPLETED");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PaymentService payments;

    @MockitoBean
    JwtDecoder decoder;

    @BeforeEach
    void tokens() {
        when(decoder.decode(anyString())).thenAnswer(invocation -> token(invocation.getArgument(0)));
    }

    @Test
    void firstPaymentReturns201WithLocation() throws Exception {
        when(payments.create(eq("key-1"), any())).thenReturn(new CreatedPayment(COMPLETED, true));
        mvc.perform(create("payment-operator", "key-1"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/payments/" + PAYMENT))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void repeatedKeyReturns200WithStoredPayment() throws Exception {
        when(payments.create(eq("key-1"), any())).thenReturn(new CreatedPayment(COMPLETED, false));
        mvc.perform(create("payment-operator", "key-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(PAYMENT.toString()));
    }

    @Test
    void sameKeyWithDifferentBodyReturns422() throws Exception {
        when(payments.create(eq("key-1"), any())).thenThrow(new PaymentMismatchException());
        mvc.perform(create("payment-operator", "key-1"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.path").value("/api/v1/payments"));
    }

    @Test
    void orderPaidUnderAnotherKeyReturns409() throws Exception {
        when(payments.create(eq("key-2"), any())).thenThrow(new PaymentConflictException());
        mvc.perform(create("payment-operator", "key-2")).andExpect(status().isConflict());
    }

    @Test
    void missingIdempotencyKeyReturns400() throws Exception {
        mvc.perform(create("payment-operator", null)).andExpect(status().isBadRequest());
        verify(payments, never()).create(anyString(), any());
    }

    @Test
    void idempotencyKeyLongerThan100CharactersReturns400() throws Exception {
        mvc.perform(create("payment-operator", "k".repeat(101))).andExpect(status().isBadRequest());
        verify(payments, never()).create(anyString(), any());
    }

    @Test
    void nonPositiveAmountReturns400() throws Exception {
        mvc.perform(post("/api/v1/payments").header("Authorization", "Bearer payment-operator")
                        .header("Idempotency-Key", "key-1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"" + ORDER + "\",\"amount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("amount")));
    }

    @Test
    void orderServiceTokenIsForbidden() throws Exception {
        mvc.perform(create("order-service", "key-1")).andExpect(status().isForbidden());
        verify(payments, never()).create(anyString(), any());
    }

    @Test
    void gatewayServiceTokenIsForbidden() throws Exception {
        mvc.perform(create("gateway-service", "key-1")).andExpect(status().isForbidden());
    }

    @Test
    void tokenWithoutPaymentAudienceIsUnauthorized() throws Exception {
        when(decoder.decode("wrong-audience")).thenThrow(new JwtValidationException("audience",
                List.of(new OAuth2Error("invalid_token", "Token audience must include payment-service", null))));
        mvc.perform(create("wrong-audience", "key-1")).andExpect(status().isUnauthorized());
    }

    @Test
    void refundOfUnknownPaymentReturns404() throws Exception {
        when(payments.refund(PAYMENT)).thenThrow(new PaymentNotFoundException());
        mvc.perform(post("/api/v1/payments/" + PAYMENT + "/refund").header("Authorization", "Bearer payment-operator"))
                .andExpect(status().isNotFound());
    }

    private MockHttpServletRequestBuilder create(String client, String key) {
        MockHttpServletRequestBuilder request = post("/api/v1/payments")
                .header("Authorization", "Bearer " + client)
                .contentType(MediaType.APPLICATION_JSON).content(BODY);
        return key == null ? request : request.header("Idempotency-Key", key);
    }

    private Jwt token(String client) {
        return Jwt.withTokenValue(client)
                .header("alg", "none")
                .subject(client)
                .audience(List.of("payment-service"))
                .claim("azp", client)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }
}
