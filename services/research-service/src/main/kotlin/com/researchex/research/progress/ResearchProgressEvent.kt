package com.researchex.research.progress

import com.researchex.contract.research.ResearchQueryStatus
import java.time.Instant

/**
 * SSE로 스트리밍할 연구 질의 진행률 이벤트 도메인 객체.
 * - [ResearchQueryStatus] 를 통해 현재 상태를 전달한다.
 * - 진행 퍼센트, 메시지, 에러 정보 등을 포함해 클라이언트에서 상세 상태를 표현할 수 있도록 한다.
 */
data class ResearchProgressEvent(
    val eventId: String,
    val occurredAt: Instant,
    val tenantId: String,
    val queryId: String,
    val status: ResearchQueryStatus,
    val progressPercentage: Double,
    val rowCount: Long?,
    val resultLocation: String?,
    val message: String?,
    val errorCode: String?,
    val errorMessage: String?
) {
    init {
        require(!progressPercentage.isNaN() && !progressPercentage.isInfinite()) {
            "progressPercentage는 숫자여야 합니다."
        }
    }

    fun withClampedProgress(): ResearchProgressEvent {
        val clamped = progressPercentage.coerceIn(0.0, 100.0)
        return if (clamped == progressPercentage) this else copy(progressPercentage = clamped)
    }

    companion object {
        fun of(
            eventId: String,
            occurredAt: Instant,
            tenantId: String,
            queryId: String,
            status: ResearchQueryStatus,
            progressPercentage: Double,
            rowCount: Long?,
            resultLocation: String?,
            message: String?,
            errorCode: String?,
            errorMessage: String?
        ): ResearchProgressEvent {
            return ResearchProgressEvent(
                eventId = eventId,
                occurredAt = occurredAt,
                tenantId = tenantId,
                queryId = queryId,
                status = status,
                progressPercentage = progressPercentage.coerceIn(0.0, 100.0),
                rowCount = rowCount,
                resultLocation = resultLocation,
                message = message,
                errorCode = errorCode,
                errorMessage = errorMessage
            )
        }
    }
}
