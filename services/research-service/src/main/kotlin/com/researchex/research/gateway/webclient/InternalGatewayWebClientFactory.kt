package com.researchex.research.gateway.webclient

import com.researchex.common.gateway.InternalGatewayHeaderProvider
import com.researchex.research.gateway.GatewayClientProperties
import io.netty.channel.ChannelOption
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.netty.http.client.HttpClient

/**
 * 내부 통신용 WebClient 인스턴스를 생성하는 헬퍼.
 * 연결/읽기 타임아웃과 공통 헤더 주입 로직을 중앙에서 관리한다.
 */
@Component
class InternalGatewayWebClientFactory(
    private val baseBuilder: WebClient.Builder,
    private val headerProvider: InternalGatewayHeaderProvider
) {

    /**
     * 주어진 서비스 설정을 기반으로 WebClient를 생성한다.
     */
    fun create(serviceProperties: GatewayClientProperties.Service): WebClient {
        val connectTimeoutMillis = serviceProperties.connectTimeout.toMillis()
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()

        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis)
            .responseTimeout(serviceProperties.readTimeout)
            .followRedirect(true)

        return baseBuilder.clone()
            .baseUrl(serviceProperties.baseUrl)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .filter(headerPropagationFilter())
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .build()
    }

    private fun headerPropagationFilter(): ExchangeFilterFunction {
        return ExchangeFilterFunction.ofRequestProcessor { request ->
            val mutated = ClientRequest.from(request)
                .headers { headers -> headerProvider.enrich(headers) }
                .build()
            Mono.just(mutated)
        }
    }
}
