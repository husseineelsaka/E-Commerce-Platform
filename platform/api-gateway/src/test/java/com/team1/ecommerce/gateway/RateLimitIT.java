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

import com.team1.ecommerce.gateway.ratelimit.RateLimitConfig;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.cloud.config.enabled=false")
@ActiveProfiles({"test", "rate-limit"})
@Testcontainers
class RateLimitIT {
    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7.4.5-alpine").withExposedPorts(6379);
    private static final MockWebServer keycloak = new MockWebServer();
    private static final MockWebServer downstream = new MockWebServer();
    private static final KeyPair keyPair = signingKey();
    private static final String serviceToken = "gateway-service-test-token";

    @LocalServerPort
    int port;

    @Autowired
    RateLimitConfig.Limits limits;

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
        downstream.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return json("{}");
            }
        });
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
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("gateway.rate-limit.trusted-proxies[0]", () -> "127.0.0.1");
        registry.add("PRODUCT_SERVICE_URI", () -> downstream.url("/").toString());
        registry.add("ORDER_SERVICE_URI", () -> downstream.url("/").toString());
        registry.add("INVENTORY_SERVICE_URI", () -> downstream.url("/").toString());
    }

    @Test
    void limitsOneClientOnly() throws Exception {
        String first = token("user-sign-in", "first-user", "CUSTOMER");
        String second = token("user-sign-in", "second-user", "CUSTOMER");
        assertThat(status(first, Map.of())).isEqualTo(200);
        assertThat(status(first, Map.of())).isEqualTo(200);
        assertThat(status(first, Map.of())).isEqualTo(429);
        assertThat(status(second, Map.of())).isEqualTo(200);
    }

    @Test
    void limitsOneAnonymousIpOnly() {
        Map<String, String> first = Map.of("X-Forwarded-For", "192.0.2.10");
        Map<String, String> second = Map.of("X-Forwarded-For", "192.0.2.11");
        assertThat(status(null, first)).isEqualTo(200);
        assertThat(status(null, first)).isEqualTo(200);
        assertThat(status(null, first)).isEqualTo(429);
        assertThat(status(null, second)).isEqualTo(200);
    }

    @Test
    void forgedForwardedForFromUntrustedPeerKeepsSameQuota() {
        List<String> trusted = limits.getTrustedProxies();
        limits.setTrustedProxies(List.of());
        try {
            assertThat(status(null, Map.of("X-Forwarded-For", "192.0.2.20"))).isEqualTo(200);
            assertThat(status(null, Map.of("X-Forwarded-For", "192.0.2.21"))).isEqualTo(200);
            assertThat(status(null, Map.of("X-Forwarded-For", "192.0.2.22"))).isEqualTo(429);
        } finally {
            limits.setTrustedProxies(trusted);
        }
    }

    private int status(String token, Map<String, String> headers) {
        return response("GET", "/api/v1/products", token, headers)
                .exchangeToMono(reply -> reply.releaseBody().thenReturn(reply.statusCode().value())).block();
    }

    private WebClient.RequestHeadersSpec<?> response(String method, String path, String token, Map<String, String> headers) {
        WebClient.RequestBodySpec request = WebClient.create("http://127.0.0.1:" + port)
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

