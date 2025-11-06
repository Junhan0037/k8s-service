package com.researchex.registry.api

import java.time.OffsetDateTime

/** Registry 서비스 메타데이터 응답으로 운영 모니터링에 필요한 필드를 제공한다. */
data class ServiceMetadataResponse(
    val serviceName: String,
    val description: String,
    val timestamp: OffsetDateTime,
)
