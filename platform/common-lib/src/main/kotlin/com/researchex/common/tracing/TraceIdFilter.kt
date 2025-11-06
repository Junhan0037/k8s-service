package com.researchex.common.tracing

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.util.StringUtils
import org.springframework.web.filter.OncePerRequestFilter
import java.io.IOException
import java.util.UUID

/** TraceId를 생성하고 MDC에 주입하는 필터다. */
class TraceIdFilter(
    private val properties: TracingProperties
) : OncePerRequestFilter() {

    @Throws(ServletException::class, IOException::class)
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val headerName = properties.headerName
        val incomingTraceId = request.getHeader(headerName)
        val traceId = resolveTraceId(incomingTraceId)

        MDC.put(DEFAULT_MDC_KEY, traceId)
        response.setHeader(headerName, traceId)

        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(DEFAULT_MDC_KEY)
        }
    }

    private fun resolveTraceId(incomingTraceId: String?): String {
        if (StringUtils.hasText(incomingTraceId)) {
            return incomingTraceId!!
        }
        if (properties.generateIfMissing) {
            val generated = UUID.randomUUID().toString()
            log.debug("TraceId가 없어 새로 발급했습니다. traceId={}", generated)
            return generated
        }
        return MDC.get(DEFAULT_MDC_KEY) ?: "UNKNOWN"
    }

    companion object {
        private val log = LoggerFactory.getLogger(TraceIdFilter::class.java)
        private const val DEFAULT_MDC_KEY = "traceId"
    }
}
