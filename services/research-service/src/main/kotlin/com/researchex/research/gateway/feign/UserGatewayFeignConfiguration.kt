package com.researchex.research.gateway.feign

import com.researchex.common.gateway.InternalGatewayHeaderProvider
import com.researchex.research.gateway.GatewayClientProperties
import feign.Request
import feign.RequestInterceptor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders

/** 사용자 게이트웨이 Feign 클라이언트에서 사용할 설정 모음이다. */
@Configuration
class UserGatewayFeignConfiguration(
    private val properties: GatewayClientProperties
) {

    @Bean
    fun userGatewayHeaderInterceptor(
        headerProvider: InternalGatewayHeaderProvider
    ): RequestInterceptor {
        return RequestInterceptor { template ->
            val headers = HttpHeaders()
            headerProvider.enrich(headers)
            headers.forEach { name, values ->
                values.forEach { value -> template.header(name, value) }
            }
        }
    }

    @Bean
    fun userGatewayRequestOptions(): Request.Options {
        val service = properties.user
        return Request.Options(service.connectTimeout, service.readTimeout, true)
    }
}
