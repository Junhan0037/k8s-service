package com.researchex.research.service.search;

/**
 * 검색 결과 내 facet(aggregation) 정보를 표현한다.
 */
public record FacetBucket(
        String key,
        long count
) { }
