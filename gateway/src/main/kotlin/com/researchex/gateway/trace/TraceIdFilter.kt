package com.researchex.gateway.trace

import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Component
import org.springframework.util.StringUtils
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

/**
 * 모든 요청에 대해 트레이스 아이디를 추출하거나 생성한 뒤
 * 요청 및 응답 헤더로 전달하는 전역 필터다.
 * 다운스트림 서비스는 해당 헤더를 사용해 분산 트레이싱 및 로깅 상관관계를 맞출 수 있다.
 */
@Component
class TraceIdFilter(
    private val traceIdGenerator: TraceIdGenerator
) : GlobalFilter, Ordered {

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        // 클라이언트가 이미 TraceId를 보냈다면 그대로 사용하고, 없을 경우 새로 생성한다.
        val providedTraceId = exchange.request.headers.getFirst(TRACE_ID_HEADER)
        val traceId = if (StringUtils.hasText(providedTraceId)) {
            providedTraceId!!.trim()
        } else {
            traceIdGenerator.generate()
        }

        val mutatedRequest: ServerHttpRequest = exchange.request.mutate()
            .headers { headers -> headers[TRACE_ID_HEADER] = traceId }
            .build()

        // 응답에도 TraceId를 노출해 클라이언트가 참조할 수 있도록 한다.
        exchange.attributes[TRACE_ID_ATTRIBUTE] = traceId
        exchange.response.headers[TRACE_ID_HEADER] = traceId

        return chain.filter(exchange.mutate().request(mutatedRequest).build())
    }

    override fun getOrder(): Int {
        // TraceId는 가장 먼저 정해져야 이후 필터 및 로깅 단계에서 활용 가능하다.
        return Ordered.HIGHEST_PRECEDENCE
    }

    companion object {
        const val TRACE_ID_HEADER: String = "X-Trace-Id"
        const val TRACE_ID_ATTRIBUTE: String = "researchex.traceId"
    }
}
