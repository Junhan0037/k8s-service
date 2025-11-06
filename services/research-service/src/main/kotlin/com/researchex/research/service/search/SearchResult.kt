package com.researchex.research.service.search

import com.researchex.research.domain.ResearchDocument

/**
 * Elasticsearch 검색 결과를 서비스 계층에 전달할 때 사용하는 전용 DTO.
 */
data class SearchResult(
    val documents: List<ResearchDocument>,
    val totalHits: Long,
    val facets: Map<String, List<FacetBucket>>
) {
    companion object {
        /**
         * 빈 결과를 반환할 때 사용되는 헬퍼.
         */
        fun empty(): SearchResult = SearchResult(emptyList(), 0L, emptyMap())
    }
}
