package com.researchex.research.service.search;

import com.researchex.research.domain.ResearchDocument;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Elasticsearch 검색 결과를 서비스 계층에 전달할 때 사용하는 전용 DTO.
 */
public record SearchResult(
        List<ResearchDocument> documents,
        long totalHits,
        Map<String, List<FacetBucket>> facets
) {

    /**
     * 빈 결과를 반환할 때 사용되는 헬퍼.
     */
    public static SearchResult empty() {
        return new SearchResult(Collections.emptyList(), 0L, Collections.emptyMap());
    }
}
