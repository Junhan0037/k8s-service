package com.researchex.research.service.search

/**
 * 검색 백엔드(Elasticsearch)에 대한 추상화. 구현체는 Elastic 쿼리를 최적화해 제공한다.
 */
fun interface ResearchSearchRepository {

    /**
     * 지정된 검색 조건으로 Elasticsearch 를 조회한다.
     */
    fun search(criteria: ResearchQueryCriteria): SearchResult
}
