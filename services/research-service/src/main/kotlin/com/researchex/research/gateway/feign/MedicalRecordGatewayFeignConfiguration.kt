package com.researchex.research.gateway.feign

import com.researchex.common.gateway.InternalGatewayHeaderProvider
import com.researchex.research.gateway.GatewayClientProperties
import feign.Request
import feign.RequestInterceptor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders

/** 의무기록 뷰어 서비스용 Feign 설정이다. */
@Configuration
class MedicalRecordGatewayFeignConfiguration(
    private val properties: GatewayClientProperties
) {

    @Bean
    fun medicalRecordHeaderInterceptor(
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
    fun medicalRecordRequestOptions(): Request.Options {
        val service = properties.medicalRecord
        return Request.Options(service.connectTimeout, service.readTimeout, true)
    }
}
