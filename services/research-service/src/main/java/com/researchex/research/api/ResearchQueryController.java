package com.researchex.research.api;

import com.researchex.research.config.SearchProperties;
import com.researchex.research.service.ResearchQueryService;
import com.researchex.research.service.dto.ResearchQueryResponse;
import com.researchex.research.service.search.ResearchQueryCriteria;
import com.researchex.research.service.search.ResearchSortOption;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 연구 검색 API 엔드포인트.
 */
@Validated
@RestController
@RequestMapping("/api/research")
public class ResearchQueryController {

    private static final Logger log = LoggerFactory.getLogger(ResearchQueryController.class);

    private final ResearchQueryService researchQueryService;
    private final SearchProperties searchProperties;

    public ResearchQueryController(ResearchQueryService researchQueryService, SearchProperties searchProperties) {
        this.researchQueryService = researchQueryService;
        this.searchProperties = searchProperties;
    }

    /**
     * 연구 검색 엔드포인트.
     *
     * @param query        전체 텍스트 검색어
     * @param page         0-based 페이지 번호
     * @param size         페이지 크기
     * @param sortParams   정렬 조건(예: updatedAt:desc). 복수 지정 가능.
     * @param filterParams 필터 조건(예: status:RECRUITING,IN_REVIEW)
     * @param useCache     캐시 사용 여부 (기본 true)
     * @return 검색 결과
     */
    @GetMapping("/search")
    public ResponseEntity<ResearchQueryResponse> search(
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "sort", required = false) List<String> sortParams,
            @RequestParam(name = "filter", required = false) List<String> filterParams,
            @RequestParam(name = "cache", defaultValue = "true") boolean useCache
    ) {
        int pageSize = size == null ? searchProperties.getDefaultPageSize() : size;
        List<ResearchSortOption> sorts = parseSorts(sortParams);
        Map<String, List<String>> filters = parseFilters(filterParams);

        ResearchQueryCriteria criteria = ResearchQueryCriteria.builder()
                .query(StringUtils.hasText(query) ? query.trim() : null)
                .page(page)
                .size(pageSize)
                .sorts(sorts)
                .filters(filters)
                .useCache(useCache)
                .build();

        log.debug("Research 검색 요청 수신. criteria={}", criteria.toCacheKeySuffix());
        ResearchQueryResponse response = researchQueryService.search(criteria);
        return ResponseEntity.ok(response);
    }

    private List<ResearchSortOption> parseSorts(List<String> sortParams) {
        if (CollectionUtils.isEmpty(sortParams)) {
            return List.of();
        }
        List<String> whitelist = searchProperties.getSortableFields().stream()
                .map(field -> field.toLowerCase(Locale.ROOT))
                .toList();
        List<ResearchSortOption> sorts = new ArrayList<>();
        for (String sortParam : sortParams) {
            if (!StringUtils.hasText(sortParam)) {
                continue;
            }
            ResearchSortOption option = ResearchSortOption.fromParameter(sortParam);
            if (!whitelist.contains(option.field().toLowerCase(Locale.ROOT))) {
                continue;
            }
            sorts.add(option);
        }
        return sorts;
    }

    private Map<String, List<String>> parseFilters(List<String> filterParams) {
        if (CollectionUtils.isEmpty(filterParams)) {
            return Map.of();
        }
        Map<String, List<String>> filters = new HashMap<>();
        for (String filter : filterParams) {
            if (!StringUtils.hasText(filter) || !filter.contains(":")) {
                continue;
            }
            String[] tokens = filter.split(":", 2);
            String field = tokens[0].trim();
            List<String> values = Arrays.stream(tokens[1].split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toList());
            if (values.isEmpty()) {
                continue;
            }
            filters.merge(field, values, (existing, incoming) -> {
                List<String> merged = new ArrayList<>(existing);
                merged.addAll(incoming);
                return merged;
            });
        }
        return filters;
    }
}
