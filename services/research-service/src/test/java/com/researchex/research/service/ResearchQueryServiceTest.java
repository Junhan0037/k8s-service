package com.researchex.research.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.researchex.contract.research.ResearchQueryStatus;
import com.researchex.research.config.SearchProperties;
import com.researchex.research.domain.ResearchDocument;
import com.researchex.research.progress.ResearchProgressService;
import com.researchex.research.service.cache.SearchResultCacheRepository;
import com.researchex.research.service.dto.FacetBucketResponse;
import com.researchex.research.service.dto.PaginationMetadata;
import com.researchex.research.service.dto.ResearchQueryResponse;
import com.researchex.research.service.search.FacetBucket;
import com.researchex.research.service.search.ResearchQueryCriteria;
import com.researchex.research.service.search.ResearchSearchRepository;
import com.researchex.research.service.search.SearchResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link ResearchQueryService} 의 캐시 및 진행률 연동 동작을 검증한다.
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
    private ResearchQueryService researchQueryService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        searchProperties = new SearchProperties();
        researchQueryService = new ResearchQueryService(
                searchRepository,
                cacheRepository,
                objectMapper,
                searchProperties,
                new SimpleMeterRegistry(),
                progressService
        );
    }

    @Test
    void search_returnsCachedResponseWhenPresent_andPublishesProgress() throws Exception {
        ResearchQueryCriteria criteria = ResearchQueryCriteria.builder()
                .query("immunotherapy")
                .page(0)
                .size(10)
                .queryId("cached-qid")
                .build();

        ResearchQueryResponse cachedResponse = new ResearchQueryResponse(
                null,
                ResearchQueryStatus.COMPLETED,
                List.of(),
                PaginationMetadata.from(0, 10, 0),
                Map.of("phase", List.of(new FacetBucketResponse("PHASE_1", 3)))
        );
        String serialized = objectMapper.writeValueAsString(cachedResponse);
        given(cacheRepository.find(any())).willReturn(Optional.of(serialized));

        ResearchQueryResponse response = researchQueryService.search(criteria);

        assertThat(response.queryId()).isEqualTo("cached-qid");
        assertThat(response.status()).isEqualTo(ResearchQueryStatus.COMPLETED);
        verify(searchRepository, never()).search(any());
        verify(progressService).publishPending(eq("default"), eq("cached-qid"), anyString());
        verify(progressService).publishCompleted(eq("default"), eq("cached-qid"), eq(0L), eq(true), isNull());
    }

    @Test
    void search_executesRepositoryAndCachesOnMiss_andPublishesProgress() {
        ResearchQueryCriteria criteria = ResearchQueryCriteria.builder()
                .query("oncology")
                .page(1)
                .size(5)
                .queryId("miss-qid")
                .build();

        ResearchDocument document = ResearchDocument.builder()
                .id("RS-1")
                .title("Cancer Study")
                .summary("Phase 2 cancer study")
                .diseaseCodes(List.of("C34"))
                .tags(List.of("oncology"))
                .principalInvestigatorId("PI-1")
                .principalInvestigatorName("Alice")
                .institution("General Hospital")
                .phase("PHASE_2")
                .status("RECRUITING")
                .enrollment(120d)
                .createdAt(OffsetDateTime.parse("2024-04-01T10:00:00+09:00"))
                .updatedAt(OffsetDateTime.parse("2024-04-10T10:00:00+09:00"))
                .build();

        SearchResult searchResult = new SearchResult(
                List.of(document),
                1,
                Map.of("phase", List.of(new FacetBucket("PHASE_2", 1L)))
        );

        given(cacheRepository.find(any())).willReturn(Optional.empty());
        given(searchRepository.search(criteria)).willReturn(searchResult);

        ResearchQueryResponse response = researchQueryService.search(criteria);

        assertThat(response.queryId()).isEqualTo("miss-qid");
        assertThat(response.status()).isEqualTo(ResearchQueryStatus.COMPLETED);
        assertThat(response.items()).hasSize(1);
        assertThat(response.facets()).containsKey("phase");

        ArgumentCaptor<String> cacheKeyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(cacheRepository).put(cacheKeyCaptor.capture(), payloadCaptor.capture());

        assertThat(cacheKeyCaptor.getValue()).contains("oncology");
        assertThat(payloadCaptor.getValue()).isNotBlank();
        verify(progressService).publishPending(eq("default"), eq("miss-qid"), anyString());
        verify(progressService).publishRunning(eq("default"), eq("miss-qid"), eq(30.0d), isNull(), anyString());
        verify(progressService).publishCompleted(eq("default"), eq("miss-qid"), eq(1L), eq(false), isNull());
    }
}
