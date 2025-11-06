package com.researchex.research.application

import com.researchex.research.domain.ResearchIndexDocument
import com.researchex.research.infrastructure.InMemoryResearchIndexRepository
import io.micrometer.observation.annotation.Observed
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.scheduler.Scheduler
import java.util.Optional

/**
 * 연구 인덱스 조회용 애플리케이션 서비스.
 *
 * 동적 캐시 서비스와 Reactor 스케줄러를 조합해 조회 부하를 줄이면서도 논블로킹 컨트롤러 계약을 유지한다.
 * 캐시는 동기 로직으로 수행하고, 컨트롤러에는 Mono 인터페이스를 제공해 기존 WebFlux 흐름을 그대로 유지한다.
 */
@Service
class ResearchIndexQueryService(
    private val cacheService: ResearchIndexCacheService,
    private val repository: InMemoryResearchIndexRepository,
    private val researchIoScheduler: Scheduler
) {

    /** 전체 문서 목록은 캐싱 효과가 낮으므로 기존 비동기 저장소 호출을 그대로 노출한다. */
    @Observed(
        name = "researchex.research.index.find-all",
        contextualName = "research-index-find-all",
        lowCardinalityKeyValues = ["operation", "findAll"]
    )
    fun findAll(): Mono<List<ResearchIndexDocument>> = repository.findAll()

    /**
     * 단건 조회 시 캐시 계층을 거친 결과를 Optional 형태로 반환한다.
     * Mono로 래핑해 컨트롤러에서 논블로킹 방식으로 처리할 수 있도록 한다.
     */
    @Observed(
        name = "researchex.research.index.find-by-id",
        contextualName = "research-index-find-by-id",
        lowCardinalityKeyValues = ["operation", "findById"]
    )
    fun findByDocumentId(documentId: String): Mono<Optional<ResearchIndexDocument>> {
        return Mono.fromCallable {
            Optional.ofNullable(cacheService.findDocument(documentId))
        }.subscribeOn(researchIoScheduler)
    }
}
