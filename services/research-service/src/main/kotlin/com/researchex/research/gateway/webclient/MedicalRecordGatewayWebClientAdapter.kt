package com.researchex.research.gateway.webclient

import com.researchex.research.gateway.GatewayClientProperties
import com.researchex.research.gateway.MedicalRecordGateway
import com.researchex.research.gateway.dto.MedicalRecordSummaryResponse
import com.researchex.research.gateway.exception.InternalGatewayException
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

/**
 * WebClient 기반 의무기록 서비스 게이트웨이 구현.
 * 헤더 전파 및 타임아웃 설정을 일관되게 적용해 서비스 간 호출 안정성을 높인다.
 */
@Component
@ConditionalOnProperty(
    prefix = "researchex.gateway.medical-record",
    name = ["client-type"],
    havingValue = "WEBCLIENT",
    matchIfMissing = true
)
class MedicalRecordGatewayWebClientAdapter(
    properties: GatewayClientProperties,
    clientFactory: InternalGatewayWebClientFactory
) : MedicalRecordGateway {

    private val serviceProperties = properties.medicalRecord
    private val client: WebClient = clientFactory.create(serviceProperties)

    override fun fetchLatestRecord(patientId: String): Mono<MedicalRecordSummaryResponse> {
        return client.get()
            .uri { builder -> builder.path("/internal/patients/{patientId}/records/latest").build(patientId) }
            .retrieve()
            .bodyToMono(MedicalRecordSummaryResponse::class.java)
            .timeout(serviceProperties.readTimeout)
            .onErrorMap { throwable ->
                InternalGatewayException("의무기록 서비스 호출 실패 (patientId=$patientId)", throwable)
            }
    }
}
