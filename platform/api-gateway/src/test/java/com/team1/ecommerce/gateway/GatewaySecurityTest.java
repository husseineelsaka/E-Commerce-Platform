package com.team1.ecommerce.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.cloud.config.enabled=false")
@ActiveProfiles("test")
class GatewaySecurityTest {
    private static final MockWebServer keycloak = new MockWebServer();
    private static final MockWebServer downstream = new MockWebServer();
    private static final KeyPair keyPair = signingKey();
    private static final String serviceToken = "gateway-service-test-token";

    @LocalServerPort
    int port;

    @BeforeAll
    static void startServers() throws IOException {
        keycloak.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if (path.contains(".well-known")) {
                    String issuer = keycloak.url("/realms/ecommerce-platform").toString().replaceAll("/$", "");
                    return json("{\"issuer\":\"" + issuer + "\",\"jwks_uri\":\"" + issuer
                            + "/protocol/openid-connect/certs\",\"token_endpoint\":\"" + issuer
                            + "/protocol/openid-connect/token\"}");
                }
                if (path.endsWith("/certs")) {
                    RSAKey publicKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                            .keyID("test-key").build();
                    return json("{\"keys\":[" + publicKey.toJSONString() + "]}");
                }
                if (path.endsWith("/token")) {
                    return json("{\"access_token\":\"" + serviceToken
                            + "\",\"token_type\":\"Bearer\",\"expires_in\":3600}");
                }
                return new MockResponse().setResponseCode(404);
            }
        });
        keycloak.start();
        downstream.start();
    }

    @AfterAll
    static void stopServers() throws IOException {
        keycloak.shutdown();
        downstream.shutdown();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("KEYCLOAK_ISSUER_URI", () -> keycloak.url("/realms/ecommerce-platform").toString().replaceAll("/$", ""));
        registry.add("GATEWAY_CLIENT_SECRET", () -> "test-secret");
        registry.add("PRODUCT_SERVICE_URI", () -> downstream.url("/").toString());
        registry.add("ORDER_SERVICE_URI", () -> downstream.url("/").toString());
        registry.add("INVENTORY_SERVICE_URI", () -> downstream.url("/").toString());
    }

    @Test
    void stripsClientSuppliedIdentityHeaders() throws Exception {
        downstream.enqueue(json("{}"));
        response("GET", "/api/v1/products", token("user-sign-in", "real-customer", "CUSTOMER"),
                Map.of("X-User-Id", "forged", "X-User-Admin", "true")).retrieve().bodyToMono(String.class).block();

        RecordedRequest request = downstream.takeRequest(5, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getHeader("X-User-Id")).isEqualTo("real-customer");
        assertThat(request.getHeader("X-User-Roles")).isEqualTo("CUSTOMER");
        assertThat(request.getHeader("X-User-Admin")).isNull();
        assertThat(request.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer " + serviceToken);
    }

    @Test
    void productCreateWithoutTokenReturnsUnauthorized() {
        int before = downstream.getRequestCount();
        int status = response("POST", "/api/v1/products", null, Map.of()).exchangeToMono(r ->
                r.releaseBody().thenReturn(r.statusCode().value())).block();
        assertThat(status).isEqualTo(401);
        assertThat(downstream.getRequestCount()).isEqualTo(before);
    }

    @Test
    void customerCannotCreateProduct() throws Exception {
        int before = downstream.getRequestCount();
        int status = response("POST", "/api/v1/products", token("user-sign-in", "customer", "CUSTOMER"), Map.of())
                .exchangeToMono(r -> r.releaseBody().thenReturn(r.statusCode().value())).block();
        assertThat(status).isEqualTo(403);
        assertThat(downstream.getRequestCount()).isEqualTo(before);
    }

    @Test
    void adminCanCreateProduct() throws Exception {
        downstream.enqueue(json("{}"));
        int status = response("POST", "/api/v1/products", token("user-sign-in", "admin", "ADMIN"), Map.of())
                .exchangeToMono(r -> r.releaseBody().thenReturn(r.statusCode().value())).block();
        RecordedRequest request = downstream.takeRequest(5, TimeUnit.SECONDS);
        assertThat(status).isEqualTo(200);
        assertThat(request).isNotNull();
        assertThat(request.getHeader("X-User-Id")).isEqualTo("admin");
    }

    @Test
    void anonymousProductReadHasOnlyServiceToken() throws Exception {
        downstream.enqueue(json("{}"));
        int status = response("GET", "/api/v1/products", null, Map.of("X-User-Id", "forged", "x-user-roles", "ADMIN"))
                .exchangeToMono(r -> r.releaseBody().thenReturn(r.statusCode().value())).block();
        RecordedRequest request = downstream.takeRequest(5, TimeUnit.SECONDS);
        assertThat(status).isEqualTo(200);
        assertThat(request).isNotNull();
        assertThat(request.getHeaders().names()).noneMatch(name -> name.regionMatches(true, 0, "X-User-", 0, 7));
        assertThat(request.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer " + serviceToken);
    }

    @Test
    void internalInventoryCheckIsNotForwarded() throws Exception {
        int before = downstream.getRequestCount();
        int status = response("GET", "/api/v1/inventory/check", token("user-sign-in", "admin", "ADMIN"), Map.of())
                .exchangeToMono(r -> r.releaseBody().thenReturn(r.statusCode().value())).block();
        assertThat(status).isEqualTo(403);
        assertThat(downstream.getRequestCount()).isEqualTo(before);
    }

    @Test
    void paymentsAreNotForwarded() throws Exception {
        int before = downstream.getRequestCount();
        int status = response("POST", "/api/v1/payments", token("user-sign-in", "customer", "CUSTOMER"), Map.of())
                .exchangeToMono(r -> r.releaseBody().thenReturn(r.statusCode().value())).block();
        assertThat(status).isEqualTo(403);
        assertThat(downstream.getRequestCount()).isEqualTo(before);
    }

    @Test
    void tokenFromAnotherClientIsUnauthorized() throws Exception {
        int before = downstream.getRequestCount();
        int status = response("POST", "/api/v1/products", token("other-client", "admin", "ADMIN"), Map.of())
                .exchangeToMono(r -> r.releaseBody().thenReturn(r.statusCode().value())).block();
        assertThat(status).isEqualTo(401);
        assertThat(downstream.getRequestCount()).isEqualTo(before);
    }

    private WebClient.RequestHeadersSpec<?> response(String method, String path, String token, Map<String, String> headers) {
        WebClient.RequestBodySpec request = WebClient.create("http://localhost:" + port)
                .method(org.springframework.http.HttpMethod.valueOf(method)).uri(path);
        headers.forEach(request::header);
        if (token != null) {
            request.headers(httpHeaders -> httpHeaders.setBearerAuth(token));
        }
        return request;
    }

    private static String token(String authorizedParty, String subject, String role) throws Exception {
        String issuer = keycloak.url("/realms/ecommerce-platform").toString().replaceAll("/$", "");
        JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer(issuer).subject(subject)
                .issueTime(Date.from(Instant.now())).expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .jwtID(UUID.randomUUID().toString()).claim("azp", authorizedParty)
                .claim("realm_access", Map.of("roles", List.of(role))).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), claims);
        jwt.sign(new RSASSASigner(keyPair.getPrivate()));
        return jwt.serialize();
    }

    private static KeyPair signingKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static MockResponse json(String body) {
        return new MockResponse().setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE).setBody(body);
    }
}
