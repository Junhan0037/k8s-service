package com.researchex.gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gateway 핵심 동작(라우팅, JWT 검증, TraceId 전달)을 검증하는 통합 테스트.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayApplicationTests {

    private static final String HMAC_SECRET = "local-development-secret-key-32bytes-minimum!!";
    private static final MockWebServer mockBackend;

    static {
        try {
            mockBackend = new MockWebServer();
            mockBackend.start();
        } catch (Exception ex) {
            throw new IllegalStateException("MockWebServer 초기화에 실패했습니다.", ex);
        }
    }

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("gateway.security.jwt.hmac-secret", () -> HMAC_SECRET);
        registry.add("researchex.services.research.uri", () -> mockBackend.url("/").toString());
    }

    @AfterAll
    static void shutdownBackend() throws Exception {
        mockBackend.shutdown();
    }

    @Test
    void validJwtAllowsRoutingAndPropagatesTraceId() throws InterruptedException {
        // given
        mockBackend.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"status\":\"ok\"}")
                .addHeader("Content-Type", "application/json"));

        String traceId = "integration-test-trace";
        String jwt = createToken("user-123");

        // when
        webTestClient.get()
                .uri("/api/research/ping")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                .header("X-Trace-Id", traceId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Trace-Id", traceId)
                .expectBody()
                .jsonPath("$.status").isEqualTo("ok");

        // then
        var recordedRequest = mockBackend.takeRequest(1, TimeUnit.SECONDS);
        assertThat(recordedRequest).isNotNull();
        assertThat(recordedRequest.getPath()).isEqualTo("/ping");
        assertThat(recordedRequest.getHeader("X-Trace-Id")).isEqualTo(traceId);
        assertThat(recordedRequest.getHeader("X-Auth-Subject")).isEqualTo("user-123");
    }

    @Test
    void missingJwtReturnsUnauthorized() throws InterruptedException {
        webTestClient.get()
                .uri("/api/research/ping")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED")
                .jsonPath("$.message").isEqualTo("Authorization 헤더가 존재하지 않거나 Bearer 토큰 형식이 아닙니다.");

        assertThat(mockBackend.takeRequest(100, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void invalidJwtSignatureIsRejected() throws InterruptedException {
        String tamperedToken = createToken("user-789") + "corruption";

        webTestClient.get()
                .uri("/api/research/ping")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tamperedToken)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED");

        assertThat(mockBackend.takeRequest(100, TimeUnit.MILLISECONDS)).isNull();
    }

    private String createToken(String subject) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + Duration.ofMinutes(5).toMillis());
        return Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(Keys.hmacShaKeyFor(HMAC_SECRET.getBytes()), SignatureAlgorithm.HS256)
                .compact();
    }
}
