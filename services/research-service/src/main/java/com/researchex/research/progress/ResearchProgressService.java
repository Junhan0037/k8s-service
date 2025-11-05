package com.researchex.research.progress;

import com.researchex.contract.research.ResearchQueryStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.util.concurrent.Queues;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 연구 질의 진행률 이벤트를 발행/구독하는 도메인 서비스.
 * - Reactor {@link Sinks.Many} 를 활용해 멀티 캐스트 방식으로 다수 구독자에게 이벤트를 전달한다.
 * - 최근 이벤트를 보관해 새 구독자에게 즉시 최신 상태를 보내도록 한다.
 */
@Service
public class ResearchProgressService {

    private static final Logger log = LoggerFactory.getLogger(ResearchProgressService.class);

    private static final int REPLAY_BUFFER_SIZE = Queues.SMALL_BUFFER_SIZE;

    private final Map<String, Sinks.Many<ResearchProgressEvent>> streamPerQuery = new ConcurrentHashMap<>();

    /**
     * 기본 생성자에서 별도 초기화 작업은 필요 없다.
     */
    public ResearchProgressService() {}

    /**
     * 질의가 접수(PENDING)되었음을 발행한다.
     */
    public void publishPending(String tenantId, String queryId, String message) {
        emitEvent(tenantId, queryId, ResearchQueryStatus.PENDING, 0.0d, null, null, message, null, null);
    }

    /**
     * 질의가 실행 중(RUNNING)임을 퍼센트와 함께 발행한다.
     */
    public void publishRunning(String tenantId, String queryId, double progress, Long rowCount, String message) {
        emitEvent(tenantId, queryId, ResearchQueryStatus.RUNNING, progress, rowCount, null, message, null, null);
    }

    /**
     * 질의가 정상 완료(COMPLETED)되었음을 발행한다.
     */
    public void publishCompleted(String tenantId, String queryId, long rowCount, boolean cacheHit, String resultLocation) {
        String message = cacheHit ? "검색 결과를 캐시에서 반환했습니다." : "검색이 완료되었습니다.";
        emitEvent(tenantId, queryId, ResearchQueryStatus.COMPLETED, 100.0d, rowCount, resultLocation, message, null, null);
    }

    /**
     * 질의가 실패(FAILED)했음을 발행한다.
     */
    public void publishFailed(String tenantId, String queryId, String errorCode, String errorMessage) {
        emitEvent(tenantId, queryId, ResearchQueryStatus.FAILED, 100.0d, null, null, "검색이 실패했습니다.", errorCode, errorMessage);
    }

    /**
     * 특정 queryId 흐름을 SSE 형태로 구독할 때 사용할 Flux를 반환한다.
     */
    public Flux<ResearchProgressEvent> streamByQueryId(String tenantId, String queryId) {
        String trackingKey = toTrackingKey(tenantId, queryId);
        if (trackingKey == null) {
            return Flux.error(new IllegalArgumentException("tenantId와 queryId는 모두 필수값입니다."));
        }

        Sinks.Many<ResearchProgressEvent> sink = streamPerQuery.computeIfAbsent(trackingKey, key -> Sinks.many().replay().limit(REPLAY_BUFFER_SIZE));

        return Flux.defer(sink::asFlux)
                .doOnSubscribe(subscription -> log.debug("연구 진행률 스트림 구독이 시작되었습니다. queryId={}", queryId))
                .doFinally(signalType -> log.debug("연구 진행률 스트림 구독이 종료되었습니다. queryId={}, signal={}", queryId, signalType));
    }

    private void emitEvent(
            String tenantId,
            String queryId,
            ResearchQueryStatus status,
            double progress,
            Long rowCount,
            String resultLocation,
            String message,
            String errorCode,
            String errorMessage
    ) {
        String trackingKey = toTrackingKey(tenantId, queryId);
        if (trackingKey == null) {
            log.debug("진행률 이벤트 발행이 건너뛰어졌습니다. tenantId={}, queryId={}", tenantId, queryId);
            return;
        }

        ResearchProgressEvent event = new ResearchProgressEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                tenantId,
                queryId,
                status,
                progress,
                rowCount,
                resultLocation,
                message,
                errorCode,
                errorMessage
        );

        Sinks.Many<ResearchProgressEvent> sink = streamPerQuery.computeIfAbsent(trackingKey, key -> Sinks.many().replay().limit(REPLAY_BUFFER_SIZE));
        Sinks.EmitResult result = sink.tryEmitNext(event);

        if (result.isFailure()) {
            log.warn("진행률 이벤트 발행에 실패했습니다. tenantId={}, queryId={}, reason={}", tenantId, queryId, result);
        } else if (log.isDebugEnabled()) {
            log.debug("연구 진행률 이벤트가 발행되었습니다. tenantId={}, queryId={}, status={}", tenantId, queryId, status);
        }

        if (status == ResearchQueryStatus.COMPLETED || status == ResearchQueryStatus.FAILED) {
            sink.emitComplete(Sinks.EmitFailureHandler.FAIL_FAST);
            streamPerQuery.remove(trackingKey, sink);
        }
    }

    private String toTrackingKey(String tenantId, String queryId) {
        if (tenantId == null || tenantId.isBlank() || queryId == null || queryId.isBlank()) {
            return null;
        }

        return tenantId + ":" + queryId;
    }
}
