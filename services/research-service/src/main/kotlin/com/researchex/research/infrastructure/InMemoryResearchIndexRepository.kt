package com.researchex.research.infrastructure

import com.researchex.research.domain.ResearchIndexDocument
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import reactor.core.scheduler.Scheduler
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/** 로컬 개발 환경용 In-memory Elasticsearch 스텁. */
@Repository
class InMemoryResearchIndexRepository(
    private val researchIoScheduler: Scheduler
) {

    private val store: MutableMap<String, ResearchIndexDocument> = ConcurrentHashMap()

    /** 문서 저장을 비동기적으로 수행한다. */
    fun save(document: ResearchIndexDocument): Mono<ResearchIndexDocument> {
        return Mono.fromCallable {
            store[document.documentId] = document
            document
        }.subscribeOn(researchIoScheduler)
    }

    /** 문서를 조회한다. */
    fun findByDocumentId(documentId: String): Mono<Optional<ResearchIndexDocument>> {
        return Mono.fromCallable { Optional.ofNullable(store[documentId]) }
            .subscribeOn(researchIoScheduler)
    }

    /** 캐시 계층과 연동하기 위한 동기 조회 메서드. */
    fun findByDocumentIdSync(documentId: String): Optional<ResearchIndexDocument> {
        return Optional.ofNullable(store[documentId])
    }

    /** 전체 문서를 반환한다. (로컬 개발 관찰용) */
    fun findAll(): Mono<List<ResearchIndexDocument>> {
        return Mono.fromCallable { store.values.toList() }
            .subscribeOn(researchIoScheduler)
    }
}
