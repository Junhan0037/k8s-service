package com.researchex.gateway.security

import com.researchex.gateway.trace.TraceIdFilter
import io.jsonwebtoken.Claims
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Component
import org.springframework.util.AntPathMatcher
import org.springframework.util.StringUtils
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets

/**
 * JWT 검증을 수행하는 전역 필터다.
 * 화이트리스트에 포함되지 않은 모든 경로에 대해 Authorization 헤더의 Bearer 토큰을 검증한다.
 */
@Component
class JwtAuthenticationFilter(
    private val properties: JwtProperties,
    private val tokenVerifier: JwtTokenVerifier
) : GlobalFilter, Ordered {

    private val pathMatcher = AntPathMatcher()

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        if (!properties.enabled) {
            return chain.filter(exchange)
        }

        val requestPath = exchange.request.uri.path
        if (isWhitelisted(requestPath)) {
            return chain.filter(exchange)
        }

        val authorization = exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)
        if (!StringUtils.hasText(authorization) || !authorization!!.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange, "Authorization 헤더가 존재하지 않거나 Bearer 토큰 형식이 아닙니다.")
        }

        val rawToken = authorization.substring(BEARER_PREFIX.length)
        return try {
            val claims = tokenVerifier.verify(rawToken)
            val mutatedRequest = mutateRequestWithSubject(exchange, claims)
            exchange.attributes[CLAIMS_ATTRIBUTE] = claims
            chain.filter(exchange.mutate().request(mutatedRequest).build())
        } catch (ex: JwtVerificationException) {
            unauthorized(exchange, ex.message ?: "JWT 서명을 검증하지 못했습니다.")
        }
    }

    override fun getOrder(): Int {
        // TraceId 필터 다음에 동작해야 하므로 우선순위를 약간 낮게 설정한다.
        return Ordered.HIGHEST_PRECEDENCE + 1
    }

    private fun mutateRequestWithSubject(exchange: ServerWebExchange, claims: Claims): ServerHttpRequest {
        // 필요 시 다운스트림 서비스에서 사용자 식별에 활용할 수 있도록 Subject를 헤더로 전달한다.
        return exchange.request.mutate()
            .headers { headers -> headers[SUBJECT_HEADER] = claims.subject ?: "" }
            .build()
    }

    private fun isWhitelisted(path: String): Boolean {
        return properties.whitelistPatterns.any { pattern -> pathMatcher.match(pattern, path) }
    }

    private fun unauthorized(exchange: ServerWebExchange, message: String): Mono<Void> {
        val response = exchange.response
        response.statusCode = HttpStatus.UNAUTHORIZED
        response.headers.contentType = MediaType.APPLICATION_JSON

        val traceId = exchange.getAttribute<String>(TraceIdFilter.TRACE_ID_ATTRIBUTE).orEmpty()
        val safeMessage = escapeJson(message)
        val safeTraceId = escapeJson(traceId)
        val payload = """{"code":"UNAUTHORIZED","message":"$safeMessage","traceId":"$safeTraceId"}"""
        val body = payload.toByteArray(StandardCharsets.UTF_8)
        val buffer = response.bufferFactory().wrap(body)
        return response.writeWith(Mono.just(buffer))
    }

    private fun escapeJson(value: String?): String = value?.replace("\"", "\\\"") ?: ""

    companion object {
        private const val BEARER_PREFIX = "Bearer "
        private const val CLAIMS_ATTRIBUTE = "researchex.jwtClaims"
        private const val SUBJECT_HEADER = "X-Auth-Subject"
    }
}
