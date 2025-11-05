package com.researchex.research.progress;

import com.researchex.contract.research.ResearchQueryStatus;

import java.time.Instant;

/**
 * SSE 응답으로 직렬화되는 DTO.
 * 클라이언트는 본 DTO를 기반으로 진행 퍼센트, 메시지, 에러 정보를 화면에 노출할 수 있다.
 */
public record ResearchProgressResponse(
        String eventId,
        String tenantId,
        String queryId,
        ResearchQueryStatus status,
        double progressPercentage,
        Long rowCount,
        String resultLocation,
        String message,
        String errorCode,
        String errorMessage,
        Instant occurredAt
) {
}
