package com.researchex.research.service.dto

/** facet aggregation 결과의 단일 항목을 표현한다. */
data class FacetBucketResponse(
    val key: String,
    val count: Long
)
