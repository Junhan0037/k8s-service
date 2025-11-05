package com.researchex.research.progress;

import com.researchex.contract.research.ResearchQueryStatus;

import java.time.Instant;
import java.util.Objects;

/**
 * SSE로 스트리밍할 연구 질의 진행률 이벤트 도메인 객체.
 * - {@link ResearchQueryStatus} 를 통해 현재 상태를 전달한다.
 * - 진행 퍼센트, 메시지, 에러 정보 등을 포함해 클라이언트에서 상세 상태를 표현할 수 있도록 한다.
 */
public record ResearchProgressEvent(
        String eventId,
        Instant occurredAt,
        String tenantId,
        String queryId,
        ResearchQueryStatus status,
        double progressPercentage,
        Long rowCount,
        String resultLocation,
        String message,
        String errorCode,
        String errorMessage
) {

    /**
     * 레코드 캔니컬 생성자에서 필수 필드 검증과 퍼센트 보정을 수행한다.
     */
    public ResearchProgressEvent {
        Objects.requireNonNull(eventId, "eventId는 null일 수 없습니다.");
        Objects.requireNonNull(occurredAt, "occurredAt은 null일 수 없습니다.");
        Objects.requireNonNull(tenantId, "tenantId는 null일 수 없습니다.");
        Objects.requireNonNull(queryId, "queryId는 null일 수 없습니다.");
        Objects.requireNonNull(status, "status는 null일 수 없습니다.");
        if (Double.isNaN(progressPercentage) || Double.isInfinite(progressPercentage)) {
            throw new IllegalArgumentException("progressPercentage는 숫자여야 합니다.");
        }
        // 퍼센트 범위를 0~100 사이로 고정한다.
        progressPercentage = Math.max(0.0d, Math.min(100.0d, progressPercentage));
    }
}
