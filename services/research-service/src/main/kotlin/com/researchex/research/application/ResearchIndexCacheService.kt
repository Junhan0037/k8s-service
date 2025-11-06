package com.researchex.research.application

import com.researchex.platform.cache.CacheNames
import com.researchex.research.domain.ResearchIndexDocument
import com.researchex.research.infrastructure.InMemoryResearchIndexRepository
import io.micrometer.observation.annotation.Observed
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

/**
 * 캐시 계층(L1/L2)을 통해 연구 인덱스 문서를 제공하는 전용 서비스.
 *
 * Reactive 파이프라인과 별개로 동기 메서드를 사용해 Spring Cache 추상화를 활용하며, 캐시 미스 시
 * 저장소에서 데이터를 로딩하고 히트 시에는 Caffeine/Redis 계층을 통해 빠르게 응답한다.
 */
@Service
class ResearchIndexCacheService(
    private val repository: InMemoryResearchIndexRepository
) {

    /**
     * 문서 단건 조회를 캐싱한다. 존재하지 않는 문서는 null을 반환하며 `cacheNullValues=false`
     * 설정으로 인해 캐시되지 않는다.
     */
    @Cacheable(cacheNames = [CacheNames.DYNAMIC_QUERY], key = "'doc:' + #documentId")
    @Observed(
        name = "researchex.research.cache.miss",
        contextualName = "research-cache-miss",
        lowCardinalityKeyValues = ["cache", CacheNames.DYNAMIC_QUERY]
    )
    fun findDocument(documentId: String): ResearchIndexDocument? {
        return repository.findByDocumentIdSync(documentId).orElse(null)
    }

    /**
     * 문서 갱신 시 해당 키를 L1/L2 캐시에서 제거해 다음 요청이 최신 데이터를 로드하도록 한다.
     */
    @CacheEvict(cacheNames = [CacheNames.DYNAMIC_QUERY], key = "'doc:' + #documentId")
    fun evictDocument(documentId: String) {
        log.debug("연구 문서 캐시를 무효화했습니다. documentId={}", documentId)
    }

    /** 대량 갱신 또는 초기화 시 전체 동적 캐시를 비울 때 사용한다. */
    @CacheEvict(cacheNames = [CacheNames.DYNAMIC_QUERY], allEntries = true)
    fun evictAllDynamicCache() {
        log.debug("동적 질의 캐시 전체를 비웠습니다.")
    }

    companion object {
        private val log = LoggerFactory.getLogger(ResearchIndexCacheService::class.java)
    }
}
