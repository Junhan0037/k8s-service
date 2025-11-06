package com.researchex.research.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.researchex.contract.research.ResearchQueryStatus;
import com.researchex.research.config.SearchProperties;
import com.researchex.research.domain.ResearchDocument;
import com.researchex.research.progress.ResearchProgressService;
import com.researchex.research.service.cache.SearchResultCacheRepository;
import com.researchex.research.service.dto.PaginationMetadata;
import com.researchex.research.service.dto.FacetBucketResponse;
import com.researchex.research.service.dto.ResearchQueryResponse;
import com.researchex.research.service.dto.ResearchSummaryResponse;
import com.researchex.research.service.search.FacetBucket;
import com.researchex.research.service.search.ResearchQueryCriteria;
import com.researchex.research.service.search.ResearchSearchRepository;
import com.researchex.research.service.search.SearchResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * {@link ResearchQueryService}의 캐시 처리 및 진행률 전파 로직을 검증하는 단위 테스트.
 * 캐시 적중/미스, 예외 발생 시 흐름을 각각 검증해 계층별 테스트 전략의 기본 단위 테스트를 충족한다.
 */
@ExtendWith(MockitoExtension.class)
class ResearchQueryServiceTest {

    @Mock
    private ResearchSearchRepository searchRepository;

    @Mock
    private SearchResultCacheRepository cacheRepository;

    @Mock
    private ResearchProgressService progressService;

    private ObjectMapper objectMapper;
    private SearchProperties searchProperties;
    private SimpleMeterRegistry meterRegistry;
    private ResearchQueryService sut;

    @BeforeEach
    void setUp() {
        // 검색 응답 직렬화에 사용될 ObjectMapper는 JavaTime 모듈을 활성화해 OffsetDateTime을 안정적으로 처리한다.
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
        this.objectMapper.findAndRegisterModules();

        this.searchProperties = new SearchProperties();
        this.meterRegistry = new SimpleMeterRegistry();
        this.sut = new ResearchQueryService(
                searchRepository,
                cacheRepository,
                objectMapper,
                searchProperties,
                meterRegistry,
                progressService
        );
    }

    @AfterEach
    void tearDown() {
        meterRegistry.close();
    }

    @Test
    void searchReturnsCachedResponseWhenCacheHit() throws Exception {
        ResearchQueryCriteria criteria = ResearchQueryCriteria.builder()
                .query("oncology")
                .tenantId("tenant-alpha")
                .queryId("query-123")
                .page(0)
                .size(10)
                .build();

        ResearchSummaryResponse summary = new ResearchSummaryResponse(
                "res-1",
                "암 연구 임상 시험",
                "예시 요약",
                List.of("c00"),
                List.of("oncology"),
                "pi-100",
                "Dr. Smith",
                "General Hospital",
                "PHASE_1",
                "RECRUITING",
                1200.0,
                OffsetDateTime.now().minusDays(5),
                OffsetDateTime.now()
        );
        ResearchQueryResponse cachedResponse = new ResearchQueryResponse(
                null,
                ResearchQueryStatus.COMPLETED,
                List.of(summary),
                PaginationMetadata.from(0, 10, 1),
                Map.of("status", List.of(new FacetBucketResponse("RECRUITING", 1)))
        );
        String cachedJson = objectMapper.writeValueAsString(cachedResponse);

        when(cacheRepository.find(anyString())).thenReturn(Optional.of(cachedJson));

        ResearchQueryResponse actual = sut.search(criteria);

        assertThat(actual.queryId()).isEqualTo("query-123");
        assertThat(actual.items()).containsExactly(summary);
        assertThat(actual.facets()).containsKey("status");
        assertThat(meterRegistry.get("researchex.research.cache.hits").counter().count()).isEqualTo(1.0);
        assertThat(Optional.ofNullable(meterRegistry.find("researchex.research.cache.misses").counter()).map(Counter::count).orElse(0.0)).isEqualTo(0.0);

        verify(cacheRepository).find(anyString());
        verify(searchRepository, never()).search(any());
        verify(progressService).publishPending("tenant-alpha", "query-123", "검색 요청이 접수되었습니다.");
        verify(progressService).publishCompleted("tenant-alpha", "query-123", 1L, true, null);
        verify(progressService, never()).publishFailed(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void searchQueriesRepositoryAndCachesWhenCacheMiss() throws Exception {
        ResearchQueryCriteria criteria = ResearchQueryCriteria.builder()
                .query("cardiology")
                .tenantId("tenant-beta")
                .queryId("query-456")
                .page(1)
                .size(5)
                .build();

        ResearchDocument document = ResearchDocument.builder()
                .id("doc-9")
                .title("심장 연구")
                .summary("샘플 요약")
                .diseaseCodes(List.of("i10"))
                .tags(List.of("cardio"))
                .principalInvestigatorId("pi-200")
                .principalInvestigatorName("Dr. Park")
                .institution("Heart Center")
                .phase("PHASE_2")
                .status("COMPLETED")
                .enrollment(800.0)
                .createdAt(OffsetDateTime.now().minusDays(10))
                .updatedAt(OffsetDateTime.now())
                .build();

        SearchResult searchResult = new SearchResult(
                List.of(document),
                1,
                Map.of("status", List.of(new FacetBucket("COMPLETED", 1)))
        );

        when(cacheRepository.find(anyString())).thenReturn(Optional.empty());
        when(searchRepository.search(any())).thenReturn(searchResult);

        ResearchQueryResponse response = sut.search(criteria);

        assertThat(response.status()).isEqualTo(ResearchQueryStatus.COMPLETED);
        assertThat(response.items()).hasSize(1);
        assertThat(response.queryId()).isEqualTo("query-456");
        assertThat(meterRegistry.get("researchex.research.cache.misses").counter().count()).isEqualTo(1.0);

        verify(progressService).publishPending("tenant-beta", "query-456", "검색 요청이 접수되었습니다.");
        verify(progressService).publishRunning("tenant-beta", "query-456", 30.0d, null, "Elasticsearch 검색을 실행합니다.");
        verify(progressService).publishCompleted("tenant-beta", "query-456", 1L, false, null);

        ArgumentCaptor<String> cachePayloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(cacheRepository).put(anyString(), cachePayloadCaptor.capture());

        ResearchQueryResponse cachedPayload = objectMapper.readValue(cachePayloadCaptor.getValue(), ResearchQueryResponse.class);
        assertThat(cachedPayload.queryId()).isNull();
        assertThat(cachedPayload.items()).hasSize(1);
    }

    @Test
    void searchPublishesFailedAndRethrowsWhenRepositoryThrows() {
        ResearchQueryCriteria criteria = ResearchQueryCriteria.builder()
                .query("neurology")
                .tenantId("tenant-gamma")
                .queryId("query-789")
                .page(0)
                .size(10)
                .build();

        when(cacheRepository.find(anyString())).thenReturn(Optional.empty());
        when(searchRepository.search(any())).thenThrow(new IllegalStateException("Elasticsearch timeout"));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> sut.search(criteria));

        assertThat(exception).hasMessageContaining("Elasticsearch timeout");
        verify(progressService).publishPending("tenant-gamma", "query-789", "검색 요청이 접수되었습니다.");
        verify(progressService).publishRunning("tenant-gamma", "query-789", 30.0d, null, "Elasticsearch 검색을 실행합니다.");
        verify(progressService).publishFailed(eq("tenant-gamma"), eq("query-789"), eq("RESEARCH-SEARCH-ERROR"), anyString());
        verify(progressService, never()).publishCompleted(anyString(), anyString(), anyLong(), anyBoolean(), any());

        assertThat(meterRegistry.get("researchex.research.cache.misses").counter().count()).isEqualTo(1.0);
    }
}
