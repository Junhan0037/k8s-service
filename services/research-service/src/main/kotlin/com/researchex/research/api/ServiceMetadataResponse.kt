package com.researchex.research.api

import java.time.OffsetDateTime

/**
 * 메타데이터 응답 페이로드로 각 필드는 API 게이트웨이에서 서비스 상태를 수집할 때 활용된다.
 *
 * @param serviceName 호출 중인 서비스의 논리적 이름
 * @param description 서비스의 목적 또는 핵심 책임에 대한 요약
 * @param timestamp 응답 생성 시각(UTC offset 포함)
 */
data class ServiceMetadataResponse(
    val serviceName: String,
    val description: String,
    val timestamp: OffsetDateTime
)
