package com.researchex.deid.api

import java.time.OffsetDateTime

/** De-identification 서비스 메타데이터 응답 정의다. */
data class ServiceMetadataResponse(
    val serviceName: String,
    val description: String,
    val timestamp: OffsetDateTime,
)
