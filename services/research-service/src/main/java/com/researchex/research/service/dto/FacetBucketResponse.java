package com.researchex.research.service.dto;

/**
 * facet aggregation 결과의 단일 항목을 표현한다.
 */
public record FacetBucketResponse(
        String key,
        long count
) {
}
