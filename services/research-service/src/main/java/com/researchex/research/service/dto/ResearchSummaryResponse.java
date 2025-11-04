package com.researchex.research.service.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 검색 결과에 노출되는 연구 요약 정보를 표현하는 불변 DTO.
 * Java Record를 사용해 직렬화/역직렬화 및 equals/hashCode 구현을 단순화한다.
 */
public record ResearchSummaryResponse(
        String id,
        String title,
        String summary,
        List<String> diseaseCodes,
        List<String> tags,
        String principalInvestigatorId,
        String principalInvestigatorName,
        String institution,
        String phase,
        String status,
        Double enrollment,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
