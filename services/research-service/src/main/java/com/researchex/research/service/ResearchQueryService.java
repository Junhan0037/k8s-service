package com.researchex.research.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchex.research.config.SearchProperties;
import com.researchex.research.domain.ResearchDocument;
import com.researchex.research.service.cache.SearchResultCacheRepository;
import com.researchex.research.service.dto.FacetBucketResponse;
import com.researchex.research.service.dto.PaginationMetadata;
import com.researchex.research.service.dto.ResearchQueryResponse;
import com.researchex.research.service.dto.ResearchSummaryResponse;
import com.researchex.research.service.search.ResearchQueryCriteria;
import com.researchex.research.service.search.ResearchSearchRepository;
import com.researchex.research.service.search.SearchResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 검색 요청을 처리하고 캐시/ES 조회/메트릭 발행을 조율하는 서비스 계층.
 */
@Service
public class ResearchQueryService {

    private static final Logger log = LoggerFactory.getLogger(ResearchQueryService.class);
    private static final String CACHE_KEY_PREFIX = "search::";

    private final ResearchSearchRepository searchRepository;
    private final SearchResultCacheRepository cacheRepository;
    private final ObjectMapper objectMapper;
    private final SearchProperties properties;
    private final MeterRegistry meterRegistry;
    private final Timer searchLatencyTimer;
    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;
    private final Counter cacheBypassCounter;
    private final Counter slaViolationCounter;

    public ResearchQueryService(ResearchSearchRepository searchRepository, SearchResultCacheRepository cacheRepository, ObjectMapper objectMapper, SearchProperties properties, MeterRegistry meterRegistry) {
        this.searchRepository = searchRepository;
        this.cacheRepository = cacheRepository;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.searchLatencyTimer = Timer.builder("researchex.research.search.latency")
                .description("Research 검색 API 레이턴시")
                .publishPercentileHistogram()
                .publishPercentiles(0.5, 0.9, 0.99)
                .serviceLevelObjectives(properties.getSlaThreshold(), properties.getSlaThreshold().multipliedBy(2))
                .register(meterRegistry);
        this.cacheHitCounter = Counter.builder("researchex.research.cache.hits")
                .description("Research 검색 캐시 히트 수")
                .register(meterRegistry);
        this.cacheMissCounter = Counter.builder("researchex.research.cache.misses")
                .description("Research 검색 캐시 미스 수")
                .register(meterRegistry);
        this.cacheBypassCounter = Counter.builder("researchex.research.cache.bypass")
                .description("Cache 사용 비활성화 요청 수")
                .register(meterRegistry);
        this.slaViolationCounter = Counter.builder("researchex.research.search.sla_violations")
                .description("검색 SLA(1초) 초과 응답 수")
                .register(meterRegistry);
    }

    /**
     * 검색을 수행하고 결과를 반환한다.
     */
    public ResearchQueryResponse search(ResearchQueryCriteria criteria) {
        validatePageSize(criteria.getSize());

        if (!criteria.isUseCache()) {
            cacheBypassCounter.increment();
            return executeSearch(criteria, null);
        }

        String cacheKey = CACHE_KEY_PREFIX + criteria.toCacheKeySuffix();
        Optional<ResearchQueryResponse> cached = readFromCache(cacheKey);
        if (cached.isPresent()) {
            cacheHitCounter.increment();
            return cached.get();
        }

        cacheMissCounter.increment();

        return executeSearch(criteria, cacheKey);
    }

    private void validatePageSize(int pageSize) {
        if (pageSize <= 0 || pageSize > properties.getMaxPageSize()) {
            throw new IllegalArgumentException("요청된 페이지 크기가 허용 범위를 벗어났습니다. pageSize=" + pageSize);
        }
    }

    private Optional<ResearchQueryResponse> readFromCache(String cacheKey) {
        return cacheRepository.find(cacheKey)
                .flatMap(json -> {
                    try {
                        return Optional.of(objectMapper.readValue(json, ResearchQueryResponse.class));
                    } catch (JsonProcessingException e) {
                        log.warn("캐시 역직렬화에 실패했습니다. key={}", cacheKey, e);
                        return Optional.empty();
                    }
                });
    }

    private ResearchQueryResponse executeSearch(ResearchQueryCriteria criteria, String cacheKey) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            SearchResult searchResult = searchRepository.search(criteria);
            Duration elapsed = Duration.ofNanos(sample.stop(searchLatencyTimer));
            trackSla(criteria, elapsed);

            ResearchQueryResponse response = mapToResponse(criteria, searchResult);
            if (cacheKey != null) {
                try {
                    cacheRepository.put(cacheKey, objectMapper.writeValueAsString(response));
                } catch (JsonProcessingException e) {
                    log.warn("검색 결과 캐시 저장에 실패했습니다. key={}", cacheKey, e);
                }
            }
            return response;
        } catch (RuntimeException ex) {
            Duration elapsed = Duration.ofNanos(sample.stop(searchLatencyTimer));
            trackSla(criteria, elapsed);
            throw ex;
        }
    }

    private ResearchQueryResponse mapToResponse(ResearchQueryCriteria criteria, SearchResult searchResult) {
        List<ResearchSummaryResponse> items = searchResult.documents().stream()
                .map(this::toSummary)
                .toList();
        PaginationMetadata pagination = PaginationMetadata.from(criteria.getPage(), criteria.getSize(), searchResult.totalHits());
        Map<String, List<FacetBucketResponse>> facets = searchResult.facets().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .map(bucket -> new FacetBucketResponse(bucket.key(), bucket.count()))
                                .toList()
                ));

        return new ResearchQueryResponse(items, pagination, facets);
    }

    private ResearchSummaryResponse toSummary(ResearchDocument document) {
        return new ResearchSummaryResponse(
                document.getId(),
                document.getTitle(),
                document.getSummary(),
                defaultList(document.getDiseaseCodes()),
                defaultList(document.getTags()),
                document.getPrincipalInvestigatorId(),
                document.getPrincipalInvestigatorName(),
                document.getInstitution(),
                document.getPhase(),
                document.getStatus(),
                document.getEnrollment(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }

    private List<String> defaultList(List<String> source) {
        if (source == null) {
            return List.of();
        }

        return source.stream()
                .filter(StringUtils::hasText)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .toList();
    }

    private void trackSla(ResearchQueryCriteria criteria, Duration elapsed) {
        if (elapsed.compareTo(properties.getSlaThreshold()) > 0) {
            slaViolationCounter.increment();
            log.debug("검색 SLA 초과. elapsed={}ms, criteria={}", elapsed.toMillis(), criteria);
        }
    }
}
