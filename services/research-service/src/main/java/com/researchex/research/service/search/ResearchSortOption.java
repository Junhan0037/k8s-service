package com.researchex.research.service.search;

import co.elastic.clients.elasticsearch._types.SortOrder;

import java.util.Locale;

/**
 * 사용자가 지정한 정렬 조건을 표현한다.
 */
public record ResearchSortOption(
        String field,
        SortOrder direction
) {

    /**
     * 정렬 파라미터 문자열을 파싱한다. (예: "updatedAt:desc")
     */
    public static ResearchSortOption fromParameter(String parameter) {
        if (parameter == null || parameter.isBlank()) {
            throw new IllegalArgumentException("정렬 파라미터가 비어있습니다.");
        }
        String[] tokens = parameter.split(":", 2);
        String fieldName = tokens[0].trim();
        String directionToken = tokens.length > 1 ? tokens[1].trim().toLowerCase(Locale.ROOT) : "desc";
        SortOrder order = switch (directionToken) {
            case "asc", "ascending" -> SortOrder.Asc;
            case "desc", "descending" -> SortOrder.Desc;
            default -> throw new IllegalArgumentException("지원하지 않는 정렬 방향: " + directionToken);
        };
        return new ResearchSortOption(fieldName, order);
    }

    /**
     * 캐시 키 생성을 위한 Compact 문자열.
     */
    public String asCacheFragment() {
        return field + ":" + direction.jsonValue();
    }
}
