package com.researchex.research.service.search

/** 검색 결과 내 facet(aggregation) 정보를 표현한다. */
data class FacetBucket(
    val key: String,
    val count: Long
)
