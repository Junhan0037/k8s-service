package com.researchex.common.tracing

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingRequestWrapper
import org.springframework.web.util.ContentCachingResponseWrapper
import java.io.IOException
import java.nio.charset.StandardCharsets

/** 요청/응답을 마스킹 후 로깅하는 필터다. */
class HttpLoggingFilter(
    private val loggingProperties: TracingProperties.HttpLogging
) : OncePerRequestFilter() {

    @Throws(ServletException::class, IOException::class)
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (!loggingProperties.enabled) {
            filterChain.doFilter(request, response)
            return
        }

        val requestWrapper = ContentCachingRequestWrapper(request, loggingProperties.maxPayloadLength)
        val responseWrapper = ContentCachingResponseWrapper(response)

        val startAt = System.currentTimeMillis()
        try {
            filterChain.doFilter(requestWrapper, responseWrapper)
        } finally {
            val duration = System.currentTimeMillis() - startAt
            logExchange(requestWrapper, responseWrapper, duration)
            responseWrapper.copyBodyToResponse()
        }
    }

    private fun logExchange(
        request: ContentCachingRequestWrapper,
        response: ContentCachingResponseWrapper,
        duration: Long
    ) {
        try {
            val traceId = MDC.get("traceId")
            val method = request.method
            val uri = request.requestURI
            val status = response.status
            val requestBody = if (loggingProperties.includeRequestBody) {
                extractBody(request.contentAsByteArray)
            } else {
                ""
            }
            val responseBody = if (loggingProperties.includeResponseBody) {
                extractBody(response.contentAsByteArray)
            } else {
                ""
            }

            log.info(
                "[HTTP] traceId={} {} {} status={} durationMs={} reqBody={} respBody={}",
                traceId,
                method,
                uri,
                status,
                duration,
                requestBody,
                responseBody
            )
        } catch (ex: Exception) {
            log.warn("HTTP 로깅 중 오류가 발생했습니다.", ex)
        }
    }

    private fun extractBody(content: ByteArray?): String {
        if (content == null || content.isEmpty()) {
            return ""
        }
        val raw = String(content, StandardCharsets.UTF_8)
        return SensitiveDataMasker.mask(truncate(raw))
    }

    private fun truncate(raw: String): String {
        if (raw.length <= loggingProperties.maxPayloadLength) {
            return raw
        }
        return raw.substring(0, loggingProperties.maxPayloadLength) + "...(truncated)"
    }

    companion object {
        private val log = LoggerFactory.getLogger(HttpLoggingFilter::class.java)
    }
}
