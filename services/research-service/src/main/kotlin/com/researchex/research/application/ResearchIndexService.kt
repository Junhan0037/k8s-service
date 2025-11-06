package com.researchex.research.application

import com.researchex.contract.deid.DeidJobEvent
import com.researchex.contract.deid.DeidStage
import com.researchex.research.domain.ResearchIndexDocument
import com.researchex.research.infrastructure.InMemoryResearchIndexRepository
import io.micrometer.observation.annotation.Observed
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.scheduler.Scheduler
import java.time.Instant
import java.util.UUID

/** 가명화 완료 이벤트를 받아 연구 인덱스를 최신 상태로 유지한다. */
@Service
class ResearchIndexService(
    private val repository: InMemoryResearchIndexRepository,
    private val researchCpuScheduler: Scheduler,
    private val cacheService: ResearchIndexCacheService
) {

    /** Deid Job 이벤트를 처리해 인덱스를 업데이트한다. */
    @Observed(
        name = "researchex.research.deid.indexing",
        contextualName = "deid-indexing",
        lowCardinalityKeyValues = ["stage", "completed"]
    )
    fun handleDeidJobEvent(event: DeidJobEvent): Mono<Void> {
        if (event.stage != DeidStage.COMPLETED) {
            log.debug("Deid 이벤트가 COMPLETED 단계가 아니므로 인덱싱을 건너뜁니다. stage={}", event.stage)
            return Mono.empty()
        }

        return Mono.fromCallable { mapToDocument(event) }
            .subscribeOn(researchCpuScheduler)
            .flatMap { document -> repository.save(document) }
            .doOnSuccess { document ->
                log.info(
                    "연구 인덱스가 갱신되었습니다. tenantId={}, jobId={}, documentId={}",
                    document.tenantId,
                    document.jobId,
                    document.documentId
                )
                cacheService.evictDocument(document.documentId)
            }
            .then()
    }

    private fun mapToDocument(event: DeidJobEvent): ResearchIndexDocument {
        val documentId = UUID.randomUUID().toString()
        return ResearchIndexDocument(
            documentId = documentId,
            tenantId = event.tenantId,
            jobId = event.jobId,
            payloadLocation = event.payloadLocation,
            indexedAt = Instant.now()
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(ResearchIndexService::class.java)
    }
}
