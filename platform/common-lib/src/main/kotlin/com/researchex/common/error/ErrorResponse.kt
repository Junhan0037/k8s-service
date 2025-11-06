package com.researchex.common.error

import java.time.Instant

/**
 * API 에러 응답 공통 포맷.
 *
 * @param code 에러 코드
 * @param message 사용자 메시지
 * @param traceId 추적용 TraceId
 * @param timestamp 발생 시각
 * @param details 추가 정보
 */
data class ErrorResponse(
    val code: String,
    val message: String,
    val traceId: String,
    val timestamp: Instant,
    val details: Map<String, Any?>
) {
    companion object {
        fun of(errorCode: ErrorCode, traceId: String, details: Map<String, Any?> = emptyMap()): ErrorResponse {
            return ErrorResponse(errorCode.code, errorCode.message, traceId, Instant.now(), details)
        }

        fun of(code: String, message: String, traceId: String, details: Map<String, Any?> = emptyMap()): ErrorResponse {
            return ErrorResponse(code, message, traceId, Instant.now(), details)
        }
    }
}
