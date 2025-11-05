package com.researchex.research.service.search;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 검색 요청을 표현하는 도메인 오브젝트.
 * Controller → Service → Repository 간 명확한 계약을 위해 사용한다.
 */
@Getter
@Builder
@ToString
public class ResearchQueryCriteria {

    /**
     * 사용자가 입력한 전체 텍스트 검색 키워드.
     */
    private final String query;

    /**
     * 멀티 테넌트 환경에서의 테넌트 식별자.
     */
    @Builder.Default
    private final String tenantId = "default";

    /**
     * 진행률 추적을 위한 질의 식별자.
     */
    private final String queryId;

    /**
     * 0-based 페이지 번호.
     */
    private final int page;

    /**
     * 페이지당 문서 수.
     */
    private final int size;

    /**
     * 필터 조건 (field -> values).
     */
    @Builder.Default
    private final Map<String, List<String>> filters = Collections.emptyMap();

    /**
     * 정렬 조건 목록.
     */
    @Builder.Default
    private final List<ResearchSortOption> sorts = Collections.emptyList();

    /**
     * 캐시 사용 여부. 실시간성이 필요한 요청은 false로 내려와서 즉시 Elasticsearch를 조회한다.
     */
    @Builder.Default
    private final boolean useCache = true;

    /**
     * 캐시 키 생성을 위해 page/size/sort/filter 정보를 고유 문자열로 직렬화한다.
     */
    public String toCacheKeySuffix() {
        StringBuilder builder = new StringBuilder();
        builder.append("tenant=").append(Objects.toString(tenantId, "default"))
                .append("|q=").append(Objects.toString(query, ""))
                .append("|p=").append(page)
                .append("|s=").append(size);
        if (!filters.isEmpty()) {
            builder.append("|f=").append(filters.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> entry.getKey() + ":" + String.join(",", entry.getValue()))
                    .reduce((a, b) -> a + ";" + b)
                    .orElse(""));
        }
        if (!sorts.isEmpty()) {
            builder.append("|o=").append(sorts.stream()
                    .map(ResearchSortOption::asCacheFragment)
                    .sorted()
                    .reduce((a, b) -> a + "," + b)
                    .orElse(""));
        }
        return builder.toString();
    }
}
