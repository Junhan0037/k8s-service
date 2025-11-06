package com.researchex.research.service.dto

import com.researchex.contract.research.ResearchQueryStatus

/**
 * 최종적으로 API 레이어에서 반환하는 검색 응답 DTO.
 */
data class ResearchQueryResponse(
    val queryId: String?,
    val status: ResearchQueryStatus,
    val items: List<ResearchSummaryResponse>,
    val pagination: PaginationMetadata,
    val facets: Map<String, List<FacetBucketResponse>>
)
