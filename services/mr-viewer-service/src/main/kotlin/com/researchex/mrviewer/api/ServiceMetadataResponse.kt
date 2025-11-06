package com.researchex.mrviewer.api

import java.time.OffsetDateTime

/** MR Viewer 서비스가 노출하는 기본 메타데이터 응답이다. */
data class ServiceMetadataResponse(
    val serviceName: String,
    val description: String,
    val timestamp: OffsetDateTime,
)
