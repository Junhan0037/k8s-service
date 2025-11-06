package com.researchex.gateway

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Duration
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Gateway 핵심 동작(라우팅, JWT 검증, TraceId 전달)을 검증하는 통합 테스트다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayApplicationTests {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Test
    fun validJwtAllowsRoutingAndPropagatesTraceId() {
        mockBackend.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"status\":\"ok\"}")
                .addHeader("Content-Type", "application/json")
        )

        val traceId = "integration-test-trace"
        val jwt = createToken("user-123")

        webTestClient.get()
            .uri("/api/research/ping")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $jwt")
            .header("X-Trace-Id", traceId)
            .exchange()
            .expectStatus().isOk
            .expectHeader().valueEquals("X-Trace-Id", traceId)
            .expectBody()
            .jsonPath("$.status").isEqualTo("ok")

        val recordedRequest = mockBackend.takeRequest(1, TimeUnit.SECONDS)
        assertThat(recordedRequest).isNotNull
        assertThat(recordedRequest!!.path).isEqualTo("/ping")
        assertThat(recordedRequest.getHeader("X-Trace-Id")).isEqualTo(traceId)
        assertThat(recordedRequest.getHeader("X-Auth-Subject")).isEqualTo("user-123")
    }

    @Test
    fun missingJwtReturnsUnauthorized() {
        webTestClient.get()
            .uri("/api/research/ping")
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody()
            .jsonPath("$.code").isEqualTo("UNAUTHORIZED")
            .jsonPath("$.message").isEqualTo("Authorization 헤더가 존재하지 않거나 Bearer 토큰 형식이 아닙니다.")

        assertThat(mockBackend.takeRequest(100, TimeUnit.MILLISECONDS)).isNull()
    }

    @Test
    fun invalidJwtSignatureIsRejected() {
        val tamperedToken = createToken("user-789") + "corruption"

        webTestClient.get()
            .uri("/api/research/ping")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $tamperedToken")
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody()
            .jsonPath("$.code").isEqualTo("UNAUTHORIZED")

        assertThat(mockBackend.takeRequest(100, TimeUnit.MILLISECONDS)).isNull()
    }

    companion object {
        private const val HMAC_SECRET = "local-development-secret-key-32bytes-minimum!!"
        private val mockBackend: MockWebServer = MockWebServer().apply {
            try {
                start()
            } catch (ex: Exception) {
                throw IllegalStateException("MockWebServer 초기화에 실패했습니다.", ex)
            }
        }

        @JvmStatic
        @DynamicPropertySource
        fun registerDynamicProperties(registry: DynamicPropertyRegistry) {
            registry.add("gateway.security.jwt.hmac-secret") { HMAC_SECRET }
            registry.add("researchex.services.research.uri") { mockBackend.url("/").toString() }
        }

        @JvmStatic
        @AfterAll
        fun shutdownBackend() {
            mockBackend.shutdown()
        }

        private fun createToken(subject: String): String {
            val now = Date()
            val expiry = Date(now.time + Duration.ofMinutes(5).toMillis())
            // 테스트용이므로 간단한 HMAC 서명을 사용해 토큰을 생성한다.
            return Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(Keys.hmacShaKeyFor(HMAC_SECRET.toByteArray()), SignatureAlgorithm.HS256)
                .compact()
        }
    }
}
