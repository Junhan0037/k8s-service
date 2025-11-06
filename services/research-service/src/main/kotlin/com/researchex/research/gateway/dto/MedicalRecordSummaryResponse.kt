package com.researchex.research.gateway.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.OffsetDateTime

/** 의무기록 뷰어 서비스가 반환하는 환자 요약 정보 DTO다. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class MedicalRecordSummaryResponse(
    @JsonProperty("patientId")
    val patientId: String,

    @JsonProperty("recordId")
    val recordId: String,

    @JsonProperty("summary")
    val summary: String,

    @JsonProperty("lastUpdatedAt")
    val lastUpdatedAt: OffsetDateTime
)
