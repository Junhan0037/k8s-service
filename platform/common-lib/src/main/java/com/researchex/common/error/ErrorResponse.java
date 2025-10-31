package com.researchex.common.error;

import java.time.Instant;
import java.util.Map;

/**
 * API 에러 응답 공통 포맷.
 *
 * @param code 에러 코드
 * @param message 사용자 메시지
 * @param traceId 추적용 TraceId
 * @param timestamp 발생 시각
 * @param details 추가 정보
 */
public record ErrorResponse(
        String code,
        String message,
        String traceId,
        Instant timestamp,
        Map<String, Object> details) {

    public static ErrorResponse of(ErrorCode errorCode, String traceId, Map<String, Object> details) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), traceId, Instant.now(), details);
    }

    public static ErrorResponse of(ErrorCode errorCode, String traceId) {
        return of(errorCode, traceId, Map.of());
    }

    public static ErrorResponse of(String code, String message, String traceId, Map<String, Object> details) {
        return new ErrorResponse(code, message, traceId, Instant.now(), details);
    }
}
