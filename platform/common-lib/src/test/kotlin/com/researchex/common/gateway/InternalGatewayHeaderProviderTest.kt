package com.researchex.common.gateway

import com.researchex.common.security.InternalSecurityProperties
import com.researchex.common.tracing.TracingProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.http.HttpHeaders

/** InternalGatewayHeaderProvider 동작을 검증하는 단위 테스트다. */
class InternalGatewayHeaderProviderTest {

    private val tracingProperties = TracingProperties()
    private val securityProperties = InternalSecurityProperties()

    @AfterEach
    fun tearDown() {
        MDC.clear()
    }

    @Test
    fun enrichShouldAppendTraceIdAndSecretWhenAvailable() {
        tracingProperties.headerName = "X-Trace-Id"
        securityProperties.secret = "internal-secret"
        val provider = InternalGatewayHeaderProvider(tracingProperties, securityProperties)

        MDC.put("traceId", "trace-123")
        val headers = HttpHeaders()

        provider.enrich(headers)

        assertThat(headers.getFirst("X-Trace-Id")).isEqualTo("trace-123")
        assertThat(headers.getFirst("X-Internal-Secret")).isEqualTo("internal-secret")
    }

    @Test
    fun enrichShouldNotOverrideExistingHeaders() {
        tracingProperties.headerName = "X-Trace-Id"
        securityProperties.secret = "internal-secret"
        val provider = InternalGatewayHeaderProvider(tracingProperties, securityProperties)

        val headers = HttpHeaders().apply {
            add("X-Trace-Id", "existing-trace")
            add("X-Internal-Secret", "predefined")
        }
        MDC.put("traceId", "trace-should-not-override")

        provider.enrich(headers)

        assertThat(headers.getFirst("X-Trace-Id")).isEqualTo("existing-trace")
        assertThat(headers.getFirst("X-Internal-Secret")).isEqualTo("predefined")
    }
}
