package com.researchex.cdwloader.api

import java.time.OffsetDateTime

/** CDW Loader 서비스 메타데이터 응답 구조다. */
data class ServiceMetadataResponse(
    val serviceName: String,
    val description: String,
    val timestamp: OffsetDateTime,
)
