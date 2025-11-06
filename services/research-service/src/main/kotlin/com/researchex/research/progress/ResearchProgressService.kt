package com.researchex.research.progress

import com.researchex.contract.research.ResearchQueryStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import reactor.util.concurrent.Queues
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 연구 질의 진행률 이벤트를 발행/구독하는 도메인 서비스.
 * - Reactor [Sinks.Many] 를 활용해 멀티 캐스트 방식으로 다수 구독자에게 이벤트를 전달한다.
 * - 최근 이벤트를 보관해 새 구독자에게 즉시 최신 상태를 보내도록 한다.
 */
@Service
class ResearchProgressService {

    private val streamPerQuery: MutableMap<String, Sinks.Many<ResearchProgressEvent>> = ConcurrentHashMap()

    /**
     * 질의가 접수(PENDING)되었음을 발행한다.
     */
    fun publishPending(tenantId: String?, queryId: String?, message: String?) {
        emitEvent(
            tenantId,
            queryId,
            ResearchQueryStatus.PENDING,
            progress = 0.0,
            rowCount = null,
            resultLocation = null,
            message = message,
            errorCode = null,
            errorMessage = null
        )
    }

    /**
     * 질의가 실행 중(RUNNING)임을 퍼센트와 함께 발행한다.
     */
    fun publishRunning(tenantId: String?, queryId: String?, progress: Double, rowCount: Long?, message: String?) {
        emitEvent(
            tenantId,
            queryId,
            ResearchQueryStatus.RUNNING,
            progress,
            rowCount,
            resultLocation = null,
            message = message,
            errorCode = null,
            errorMessage = null
        )
    }

    /**
     * 질의가 정상 완료(COMPLETED)되었음을 발행한다.
     */
    fun publishCompleted(tenantId: String?, queryId: String?, rowCount: Long, cacheHit: Boolean, resultLocation: String?) {
        val message = if (cacheHit) {
            "검색 결과를 캐시에서 반환했습니다."
        } else {
            "검색이 완료되었습니다."
        }
        emitEvent(
            tenantId,
            queryId,
            ResearchQueryStatus.COMPLETED,
            progress = 100.0,
            rowCount = rowCount,
            resultLocation = resultLocation,
            message = message,
            errorCode = null,
            errorMessage = null
        )
    }

    /**
     * 질의가 실패(FAILED)했음을 발행한다.
     */
    fun publishFailed(tenantId: String?, queryId: String?, errorCode: String?, errorMessage: String?) {
        emitEvent(
            tenantId,
            queryId,
            ResearchQueryStatus.FAILED,
            progress = 100.0,
            rowCount = null,
            resultLocation = null,
            message = "검색이 실패했습니다.",
            errorCode = errorCode,
            errorMessage = errorMessage
        )
    }

    /**
     * 특정 queryId 흐름을 SSE 형태로 구독할 때 사용할 Flux를 반환한다.
     */
    fun streamByQueryId(tenantId: String?, queryId: String?): Flux<ResearchProgressEvent> {
        val trackingKey = toTrackingKey(tenantId, queryId)
            ?: return Flux.error(IllegalArgumentException("tenantId와 queryId는 모두 필수값입니다."))

        val sink = streamPerQuery.computeIfAbsent(trackingKey) {
            Sinks.many().replay().limit(REPLAY_BUFFER_SIZE)
        }

        return Flux.defer(sink::asFlux)
            .doOnSubscribe { log.debug("연구 진행률 스트림 구독이 시작되었습니다. queryId={}", queryId) }
            .doFinally { signal -> log.debug("연구 진행률 스트림 구독이 종료되었습니다. queryId={}, signal={}", queryId, signal) }
    }

    private fun emitEvent(
        tenantId: String?,
        queryId: String?,
        status: ResearchQueryStatus,
        progress: Double,
        rowCount: Long?,
        resultLocation: String?,
        message: String?,
        errorCode: String?,
        errorMessage: String?
    ) {
        val trackingKey = toTrackingKey(tenantId, queryId)
        if (trackingKey == null) {
            log.debug("진행률 이벤트 발행이 건너뛰어졌습니다. tenantId={}, queryId={}", tenantId, queryId)
            return
        }

        val event = ResearchProgressEvent.of(
            eventId = UUID.randomUUID().toString(),
            occurredAt = Instant.now(),
            tenantId = tenantId!!,
            queryId = queryId!!,
            status = status,
            progressPercentage = progress,
            rowCount = rowCount,
            resultLocation = resultLocation,
            message = message,
            errorCode = errorCode,
            errorMessage = errorMessage
        )

        val sink = streamPerQuery.computeIfAbsent(trackingKey) {
            Sinks.many().replay().limit(REPLAY_BUFFER_SIZE)
        }
        val result = sink.tryEmitNext(event.withClampedProgress())

        when {
            result.isFailure -> log.warn(
                "진행률 이벤트 발행에 실패했습니다. tenantId={}, queryId={}, reason={}",
                tenantId,
                queryId,
                result
            )
            log.isDebugEnabled -> log.debug(
                "연구 진행률 이벤트가 발행되었습니다. tenantId={}, queryId={}, status={}",
                tenantId,
                queryId,
                status
            )
        }

        if (status == ResearchQueryStatus.COMPLETED || status == ResearchQueryStatus.FAILED) {
            sink.emitComplete(Sinks.EmitFailureHandler.FAIL_FAST)
            streamPerQuery.remove(trackingKey, sink)
        }
    }

    private fun toTrackingKey(tenantId: String?, queryId: String?): String? {
        if (!tenantId.isNullOrBlank() && !queryId.isNullOrBlank()) {
            return "$tenantId:$queryId"
        }
        return null
    }

    companion object {
        private val log = LoggerFactory.getLogger(ResearchProgressService::class.java)
        private const val REPLAY_BUFFER_SIZE: Int = Queues.SMALL_BUFFER_SIZE
    }
}
