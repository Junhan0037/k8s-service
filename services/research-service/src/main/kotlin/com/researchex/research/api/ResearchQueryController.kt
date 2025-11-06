package com.researchex.research.api

import com.researchex.research.config.SearchProperties
import com.researchex.research.service.ResearchQueryService
import com.researchex.research.service.dto.ResearchQueryResponse
import com.researchex.research.service.search.ResearchQueryCriteria
import com.researchex.research.service.search.ResearchSortOption
import jakarta.validation.constraints.Min
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.util.CollectionUtils
import org.springframework.util.StringUtils
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.Locale
import java.util.UUID

/**
 * 연구 검색 API 엔드포인트.
 */
@Validated
@RestController
@RequestMapping("/api/research")
class ResearchQueryController(
    private val researchQueryService: ResearchQueryService,
    private val searchProperties: SearchProperties
) {

    /**
     * 연구 검색 엔드포인트.
     *
     * @param query        전체 텍스트 검색어
     * @param page         0-based 페이지 번호
     * @param size         페이지 크기
     * @param sortParams   정렬 조건(예: updatedAt:desc). 복수 지정 가능.
     * @param filterParams 필터 조건(예: status:RECRUITING,IN_REVIEW)
     * @param useCache     캐시 사용 여부 (기본 true)
     * @return 검색 결과
     */
    @GetMapping("/search")
    fun search(
        @RequestParam(name = "query", required = false) query: String?,
        @RequestParam(name = "page", defaultValue = "0") @Min(0) page: Int,
        @RequestParam(name = "size", required = false) size: Int?,
        @RequestParam(name = "sort", required = false) sortParams: List<String>?,
        @RequestParam(name = "filter", required = false) filterParams: List<String>?,
        @RequestParam(name = "cache", defaultValue = "true") useCache: Boolean,
        @RequestParam(name = "queryId", required = false) queryId: String?,
        @RequestParam(name = "tenantId", required = false) tenantId: String?
    ): ResponseEntity<ResearchQueryResponse> {
        val pageSize = size ?: searchProperties.defaultPageSize
        val sorts = parseSorts(sortParams)
        val filters = parseFilters(filterParams)
        val normalizedTenantId = if (StringUtils.hasText(tenantId)) tenantId!!.trim() else "default"
        val resolvedQueryId = if (StringUtils.hasText(queryId)) queryId!!.trim() else UUID.randomUUID().toString()

        val criteria = ResearchQueryCriteria.builder()
            .query(if (StringUtils.hasText(query)) query!!.trim() else null)
            .page(page)
            .size(pageSize)
            .sorts(sorts)
            .filters(filters)
            .useCache(useCache)
            .tenantId(normalizedTenantId)
            .queryId(resolvedQueryId)
            .build()

        log.debug(
            "Research 검색 요청 수신. tenantId={}, queryId={}, criteria={}",
            normalizedTenantId,
            resolvedQueryId,
            criteria.toCacheKeySuffix()
        )
        val response = researchQueryService.search(criteria)
        return ResponseEntity.ok(response)
    }

    private fun parseSorts(sortParams: List<String>?): List<ResearchSortOption> {
        if (CollectionUtils.isEmpty(sortParams)) {
            return emptyList()
        }
        val whitelist = searchProperties.sortableFields
            .map { it.lowercase(Locale.ROOT) }
        val sorts = mutableListOf<ResearchSortOption>()
        sortParams!!.forEach { sortParam ->
            if (!StringUtils.hasText(sortParam)) {
                return@forEach
            }
            val option = ResearchSortOption.fromParameter(sortParam)
            if (!whitelist.contains(option.field.lowercase(Locale.ROOT))) {
                return@forEach
            }
            sorts += option
        }
        return sorts
    }

    private fun parseFilters(filterParams: List<String>?): Map<String, List<String>> {
        if (CollectionUtils.isEmpty(filterParams)) {
            return emptyMap()
        }
        val filters = mutableMapOf<String, MutableList<String>>()
        filterParams!!.forEach { filter ->
            if (!StringUtils.hasText(filter) || !filter.contains(":")) {
                return@forEach
            }
            val tokens = filter.split(":", limit = 2)
            val field = tokens[0].trim()
            val values = tokens[1]
                .split(",")
                .map { it.trim() }
                .filter { StringUtils.hasText(it) }
            if (values.isEmpty()) {
                return@forEach
            }
            filters.merge(field, values.toMutableList()) { existing, incoming ->
                existing.apply { addAll(incoming) }
            }
        }
        return filters
    }

    companion object {
        private val log = LoggerFactory.getLogger(ResearchQueryController::class.java)
    }
}
