package com.researchex.research.service.dto;

import java.util.List;
import java.util.Map;

/**
 * 최종적으로 API 레이어에서 반환하는 검색 응답 DTO.
 */
public record ResearchQueryResponse(
        List<ResearchSummaryResponse> items,
        PaginationMetadata pagination,
        Map<String, List<FacetBucketResponse>> facets
) {
}
