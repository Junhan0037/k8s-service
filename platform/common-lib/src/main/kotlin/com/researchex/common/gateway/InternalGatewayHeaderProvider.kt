package com.researchex.common.gateway

import com.researchex.common.security.InternalSecurityProperties
import com.researchex.common.tracing.TracingProperties
import org.slf4j.MDC
import org.springframework.http.HttpHeaders

/**
 * 내부 서비스 간 통신 시 필수 헤더(X-Trace-Id, X-Internal-Secret 등)를 주입하는 헬퍼 컴포넌트다.
 * 필터에서 생성된 TraceId와 내부 시크릿 값을 중앙에서 일관되게 관리해 각 게이트웨이 구현이 중복 로직을 갖지 않도록 한다.
 */
class InternalGatewayHeaderProvider(
    private val tracingProperties: TracingProperties,
    private val securityProperties: InternalSecurityProperties
) {

    /**
     * Headers 객체에 내부 통신용 공통 헤더를 추가한다.
     */
    fun enrich(headers: HttpHeaders) {
        val traceHeaderName = tracingProperties.headerName ?: DEFAULT_TRACE_HEADER
        val traceId = MDC.get(DEFAULT_TRACE_ID_MDC_KEY)
        if (!traceId.isNullOrBlank() && !headers.containsKey(traceHeaderName)) {
            headers[traceHeaderName] = traceId
        }

        if (securityProperties.enabled) {
            val headerName = securityProperties.headerName
            val secretValue = securityProperties.secret
            if (!headers.containsKey(headerName) && !secretValue.isNullOrBlank()) {
                // 내부 통신 전용 시크릿을 주입해 인증 우회 시도를 방지한다.
                headers[headerName] = secretValue
            }
        }
    }

    companion object {
        private const val DEFAULT_TRACE_ID_MDC_KEY = "traceId"
        private const val DEFAULT_TRACE_HEADER = "X-Trace-Id"
    }
}
