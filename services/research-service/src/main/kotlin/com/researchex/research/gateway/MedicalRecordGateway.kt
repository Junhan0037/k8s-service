package com.researchex.research.gateway

import com.researchex.research.gateway.dto.MedicalRecordSummaryResponse
import reactor.core.publisher.Mono

/** MR Viewer 서비스와 통신하기 위한 게이트웨이 인터페이스다. */
fun interface MedicalRecordGateway {

    /**
     * 환자의 최신 요약 의무기록을 조회한다.
     *
     * @param patientId 환자 식별자
     * @return 의무기록 요약 응답
     */
    fun fetchLatestRecord(patientId: String): Mono<MedicalRecordSummaryResponse>
}
