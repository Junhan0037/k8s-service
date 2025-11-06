package com.researchex.research.gateway.feign

import com.researchex.research.gateway.dto.MedicalRecordSummaryResponse
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

/**
 * 의무기록 뷰어 서비스와 통신하는 Feign 클라이언트.
 * 연결 정보와 헤더 전파는 [MedicalRecordGatewayFeignConfiguration] 에서 관리한다.
 */
@FeignClient(
    name = "medicalRecordGatewayClient",
    url = "\${researchex.gateway.medical-record.base-url}",
    configuration = [MedicalRecordGatewayFeignConfiguration::class]
)
interface MedicalRecordGatewayFeignClient {

    @GetMapping("/internal/patients/{patientId}/records/latest")
    fun fetchLatestRecord(@PathVariable("patientId") patientId: String): MedicalRecordSummaryResponse
}
