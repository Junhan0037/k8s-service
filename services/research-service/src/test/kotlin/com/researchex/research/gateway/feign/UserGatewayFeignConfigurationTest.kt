package com.researchex.research.gateway.feign

import com.researchex.common.gateway.InternalGatewayHeaderProvider
import com.researchex.common.security.InternalSecurityProperties
import com.researchex.common.tracing.TracingProperties
import com.researchex.research.gateway.GatewayClientProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import java.time.Duration

/** Feign 설정이 내부 헤더 전파를 수행하는지 검증한다. */
class UserGatewayFeignConfigurationTest {

    @AfterEach
    fun tearDown() {
        MDC.clear()
    }

    @Test
    fun interceptorShouldAttachTraceAndSecretHeaders() {
        val properties = GatewayClientProperties().apply {
            user.baseUrl = "http://localhost:8200"
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
        val configuration = UserGatewayFeignConfiguration(properties)
        val interceptor = configuration.userGatewayHeaderInterceptor(headerProvider)

        MDC.put("traceId", "trace-feign-1")
        val template = feign.RequestTemplate()
        interceptor.apply(template)

        assertThat(template.headers()["X-Trace-Id"]).containsExactly("trace-feign-1")
        assertThat(template.headers()["X-Internal-Secret"]).containsExactly("internal-secret")
    }
}
