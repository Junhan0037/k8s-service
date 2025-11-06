package com.researchex.userportal.api

import java.time.OffsetDateTime

/** User Portal 서비스가 반환하는 메타데이터 응답 모델이다. */
data class ServiceMetadataResponse(
    val serviceName: String,
    val description: String,
    val timestamp: OffsetDateTime,
)
