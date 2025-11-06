package com.researchex.research.service.dto

import java.time.OffsetDateTime

/**
 * 검색 결과에 노출되는 연구 요약 정보를 표현하는 불변 DTO.
 */
data class ResearchSummaryResponse(
    val id: String,
    val title: String,
    val summary: String,
    val diseaseCodes: List<String>,
    val tags: List<String>,
    val principalInvestigatorId: String,
    val principalInvestigatorName: String,
    val institution: String,
    val phase: String,
    val status: String,
    val enrollment: Double?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)
