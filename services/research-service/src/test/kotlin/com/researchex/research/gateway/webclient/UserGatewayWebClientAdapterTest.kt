package com.researchex.research.gateway.webclient

import com.researchex.common.gateway.InternalGatewayHeaderProvider
import com.researchex.common.security.InternalSecurityProperties
import com.researchex.common.tracing.TracingProperties
import com.researchex.research.gateway.GatewayClientProperties
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.web.reactive.function.client.WebClient
import reactor.test.StepVerifier
import java.io.IOException
import java.time.Duration
import java.util.concurrent.TimeUnit

/** WebClient 기반 게이트웨이가 필수 헤더를 전파하는지 검증한다. */
class UserGatewayWebClientAdapterTest {

    private lateinit var mockWebServer: MockWebServer

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
    }

    @AfterEach
    @Throws(IOException::class)
    fun tearDown() {
        MDC.clear()
        mockWebServer.shutdown()
    }

    @Test
    fun fetchUserProfileShouldPropagateInternalHeaders() {
        mockWebServer.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                        {
                          "userId": "user-1",
                          "displayName": "Dr. Kim",
                          "primaryRole": "RESEARCHER",
                          "active": true
                        }
                    """.trimIndent()
                )
        )

        val properties = GatewayClientProperties().apply {
            user.baseUrl = mockWebServer.url("/").toString()
            user.connectTimeout = Duration.ofSeconds(1)
            user.readTimeout = Duration.ofSeconds(2)
        }
        val tracingProperties = TracingProperties().apply {
            headerName = "X-Trace-Id"
        }
        val securityProperties = InternalSecurityProperties().apply {
            secret = "internal-secret"
        }
        val headerProvider = InternalGatewayHeaderProvider(tracingProperties, securityProperties)
        val factory = InternalGatewayWebClientFactory(WebClient.builder(), headerProvider)
        val adapter = UserGatewayWebClientAdapter(properties, factory)

        MDC.put("traceId", "trace-webclient-1")

        StepVerifier.create(adapter.fetchUserProfile("user-1"))
            .assertNext { response ->
                assertThat(response.userId).isEqualTo("user-1")
                assertThat(response.displayName).isEqualTo("Dr. Kim")
            }
            .verifyComplete()

        val request = mockWebServer.takeRequest(1, TimeUnit.SECONDS)
        assertThat(request).isNotNull
        assertThat(request!!.getHeader("X-Trace-Id")).isEqualTo("trace-webclient-1")
        assertThat(request.getHeader("X-Internal-Secret")).isEqualTo("internal-secret")
        assertThat(request.path).isEqualTo("/internal/users/user-1")
    }
}
