package com.researchex.research.service

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.researchex.contract.research.ResearchQueryStatus
import com.researchex.research.config.SearchProperties
import com.researchex.research.domain.ResearchDocument
import com.researchex.research.progress.ResearchProgressService
import com.researchex.research.service.cache.SearchResultCacheRepository
import com.researchex.research.service.dto.FacetBucketResponse
import com.researchex.research.service.dto.PaginationMetadata
import com.researchex.research.service.dto.ResearchQueryResponse
import com.researchex.research.service.dto.ResearchSummaryResponse
import com.researchex.research.service.search.ResearchQueryCriteria
import com.researchex.research.service.search.ResearchSearchRepository
import com.researchex.research.service.search.SearchResult
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.util.StringUtils
import java.time.Duration
import java.util.Locale

/**
 * 검색 요청을 처리하고 캐시/ES 조회/메트릭 발행을 조율하는 서비스 계층.
 */
@Service
class ResearchQueryService(
    private val searchRepository: ResearchSearchRepository,
    private val cacheRepository: SearchResultCacheRepository,
    private val objectMapper: ObjectMapper,
    private val properties: SearchProperties,
    private val meterRegistry: MeterRegistry,
    private val progressService: ResearchProgressService
) {

    private val searchLatencyTimer: Timer = Timer.builder("researchex.research.search.latency")
        .description("Research 검색 API 레이턴시")
        .publishPercentileHistogram()
        .publishPercentiles(0.5, 0.9, 0.99)
        .serviceLevelObjectives(properties.slaThreshold, properties.slaThreshold.multipliedBy(2))
        .register(meterRegistry)

    private val cacheHitCounter: Counter = Counter.builder("researchex.research.cache.hits")
        .description("Research 검색 캐시 히트 수")
        .register(meterRegistry)

    private val cacheMissCounter: Counter = Counter.builder("researchex.research.cache.misses")
        .description("Research 검색 캐시 미스 수")
        .register(meterRegistry)

    private val cacheBypassCounter: Counter = Counter.builder("researchex.research.cache.bypass")
        .description("Cache 사용 비활성화 요청 수")
        .register(meterRegistry)

    private val slaViolationCounter: Counter = Counter.builder("researchex.research.search.sla_violations")
        .description("검색 SLA(1초) 초과 응답 수")
        .register(meterRegistry)

    /**
     * 검색을 수행하고 결과를 반환한다.
     */
    fun search(criteria: ResearchQueryCriteria): ResearchQueryResponse {
        validatePageSize(criteria.size)
        val trackingContext = TrackingContext.from(criteria)

        if (trackingContext.enabled()) {
            progressService.publishPending(
                trackingContext.tenantId,
                trackingContext.queryId,
                "검색 요청이 접수되었습니다."
            )
        }

        if (!criteria.useCache) {
            cacheBypassCounter.increment()
            return executeSearch(criteria, null, trackingContext)
        }

        val cacheKey = CACHE_KEY_PREFIX + criteria.toCacheKeySuffix()
        val cached = readFromCache(cacheKey)
        if (cached != null) {
            cacheHitCounter.increment()
            val cachedResponse = attachQueryId(cached, trackingContext.queryId)
            if (trackingContext.enabled()) {
                val totalElements = cachedResponse.pagination.totalElements
                progressService.publishCompleted(
                    trackingContext.tenantId,
                    trackingContext.queryId,
                    totalElements,
                    true,
                    null
                )
            }
            return cachedResponse
        }

        cacheMissCounter.increment()
        return executeSearch(criteria, cacheKey, trackingContext)
    }

    private fun validatePageSize(pageSize: Int) {
        require(pageSize in 1..properties.maxPageSize) {
            "요청된 페이지 크기가 허용 범위를 벗어났습니다. pageSize=$pageSize"
        }
    }

    private fun readFromCache(cacheKey: String): ResearchQueryResponse? {
        val cachedJson = cacheRepository.find(cacheKey).orElse(null) ?: return null
        return try {
            objectMapper.readValue(cachedJson, ResearchQueryResponse::class.java)
        } catch (ex: JsonProcessingException) {
            log.warn("캐시 역직렬화에 실패했습니다. key={}", cacheKey, ex)
            null
        }
    }

    private fun executeSearch(
        criteria: ResearchQueryCriteria,
        cacheKey: String?,
        trackingContext: TrackingContext
    ): ResearchQueryResponse {
        val sample = Timer.start(meterRegistry)
        try {
            if (trackingContext.enabled()) {
                progressService.publishRunning(
                    trackingContext.tenantId,
                    trackingContext.queryId,
                    30.0,
                    null,
                    "Elasticsearch 검색을 실행합니다."
                )
            }

            val searchResult = searchRepository.search(criteria)
            val elapsed = Duration.ofNanos(sample.stop(searchLatencyTimer))
            trackSla(criteria, elapsed)

            val response = mapToResponse(criteria, searchResult)
            if (cacheKey != null) {
                try {
                    val payload = objectMapper.writeValueAsString(stripQueryId(response))
                    cacheRepository.put(cacheKey, payload)
                } catch (ex: JsonProcessingException) {
                    log.warn("검색 결과 캐시 저장에 실패했습니다. key={}", cacheKey, ex)
                }
            }

            if (trackingContext.enabled()) {
                progressService.publishCompleted(
                    trackingContext.tenantId,
                    trackingContext.queryId,
                    searchResult.totalHits,
                    false,
                    null
                )
            }
            return response
        } catch (ex: RuntimeException) {
            val elapsed = Duration.ofNanos(sample.stop(searchLatencyTimer))
            trackSla(criteria, elapsed)
            if (trackingContext.enabled()) {
                progressService.publishFailed(
                    trackingContext.tenantId,
                    trackingContext.queryId,
                    "RESEARCH-SEARCH-ERROR",
                    ex.message
                )
            }
            throw ex
        }
    }

    private fun mapToResponse(criteria: ResearchQueryCriteria, searchResult: SearchResult): ResearchQueryResponse {
        val items = searchResult.documents.map { toSummary(it) }
        val pagination = PaginationMetadata.from(criteria.page, criteria.size, searchResult.totalHits)
        val facets = searchResult.facets.mapValues { entry ->
            entry.value.map { bucket -> FacetBucketResponse(bucket.key, bucket.count) }
        }

        return ResearchQueryResponse(
            queryId = criteria.queryId,
            status = ResearchQueryStatus.COMPLETED,
            items = items,
            pagination = pagination,
            facets = facets
        )
    }

    private fun toSummary(document: ResearchDocument): ResearchSummaryResponse {
        val createdAt = requireNotNull(document.createdAt) { "createdAt 값이 존재해야 합니다." }
        val updatedAt = requireNotNull(document.updatedAt) { "updatedAt 값이 존재해야 합니다." }

        return ResearchSummaryResponse(
            id = document.id ?: "",
            title = document.title ?: "",
            summary = document.summary ?: "",
            diseaseCodes = defaultList(document.diseaseCodes),
            tags = defaultList(document.tags),
            principalInvestigatorId = document.principalInvestigatorId ?: "",
            principalInvestigatorName = document.principalInvestigatorName ?: "",
            institution = document.institution ?: "",
            phase = document.phase ?: "",
            status = document.status ?: "",
            enrollment = document.enrollment,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun defaultList(source: List<String>?): List<String> {
        return source
            ?.filter { StringUtils.hasText(it) }
            ?.map { value -> value.trim().lowercase(Locale.ROOT) }
            ?: emptyList()
    }

    private fun trackSla(criteria: ResearchQueryCriteria, elapsed: Duration) {
        if (elapsed > properties.slaThreshold) {
            slaViolationCounter.increment()
            log.debug("검색 SLA 초과. elapsed={}ms, criteria={}", elapsed.toMillis(), criteria)
        }
    }

    /**
     * 캐시에서 가져온 응답에 최신 queryId를 주입한다.
     */
    private fun attachQueryId(response: ResearchQueryResponse, queryId: String?): ResearchQueryResponse {
        return if (!StringUtils.hasText(queryId)) {
            response
        } else {
            response.copy(queryId = queryId!!.trim())
        }
    }

    /**
     * 캐시에 저장할 때 queryId를 제거해 재사용성을 높인다.
     */
    private fun stripQueryId(response: ResearchQueryResponse): ResearchQueryResponse {
        return response.copy(queryId = null)
    }

    private data class TrackingContext(val tenantId: String, val queryId: String?) {
        fun enabled(): Boolean = StringUtils.hasText(queryId)

        companion object {
            fun from(criteria: ResearchQueryCriteria): TrackingContext {
                val normalizedTenant = if (StringUtils.hasText(criteria.tenantId)) {
                    criteria.tenantId.trim()
                } else {
                    "default"
                }
                val normalizedQueryId = if (StringUtils.hasText(criteria.queryId)) {
                    criteria.queryId!!.trim()
                } else {
                    null
                }
                return TrackingContext(normalizedTenant, normalizedQueryId)
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ResearchQueryService::class.java)
        private const val CACHE_KEY_PREFIX = "search::"
    }
}
