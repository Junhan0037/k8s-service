package com.researchex.research.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.researchex.contract.research.ResearchQueryStatus
import com.researchex.research.config.SearchProperties
import com.researchex.research.domain.ResearchDocument
import com.researchex.research.progress.ResearchProgressService
import com.researchex.research.service.cache.SearchResultCacheRepository
import com.researchex.research.service.dto.FacetBucketResponse
import com.researchex.research.service.dto.PaginationMetadata
import com.researchex.research.service.dto.ResearchQueryResponse
import com.researchex.research.service.dto.ResearchSummaryResponse
import com.researchex.research.service.search.FacetBucket
import com.researchex.research.service.search.ResearchQueryCriteria
import com.researchex.research.service.search.ResearchSearchRepository
import com.researchex.research.service.search.SearchResult
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.any
import org.mockito.Mockito.anyBoolean
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.anyString
import org.mockito.Mockito.eq
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.time.OffsetDateTime
import java.util.Optional

/**
 * [ResearchQueryService]의 캐시 처리 및 진행률 전파 로직을 검증하는 단위 테스트.
 * 캐시 적중/미스, 예외 발생 시 흐름을 각각 검증해 계층별 테스트 전략의 기본 단위 테스트를 충족한다.
 */
@ExtendWith(MockitoExtension::class)
class ResearchQueryServiceTest {

    @Mock
    private lateinit var searchRepository: ResearchSearchRepository

    @Mock
    private lateinit var cacheRepository: SearchResultCacheRepository

    @Mock
    private lateinit var progressService: ResearchProgressService

    private lateinit var objectMapper: ObjectMapper
    private lateinit var searchProperties: SearchProperties
    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var sut: ResearchQueryService

    @BeforeEach
    fun setUp() {
        objectMapper = ObjectMapper().registerModule(JavaTimeModule()).findAndRegisterModules()
        searchProperties = SearchProperties()
        meterRegistry = SimpleMeterRegistry()
        sut = ResearchQueryService(
            searchRepository = searchRepository,
            cacheRepository = cacheRepository,
            objectMapper = objectMapper,
            properties = searchProperties,
            meterRegistry = meterRegistry,
            progressService = progressService
        )
    }

    @AfterEach
    fun tearDown() {
        meterRegistry.close()
    }

    @Test
    fun searchReturnsCachedResponseWhenCacheHit() {
        val criteria = ResearchQueryCriteria.builder()
            .query("oncology")
            .tenantId("tenant-alpha")
            .queryId("query-123")
            .page(0)
            .size(10)
            .build()

        val summary = ResearchSummaryResponse(
            id = "res-1",
            title = "암 연구 임상 시험",
            summary = "예시 요약",
            diseaseCodes = listOf("c00"),
            tags = listOf("oncology"),
            principalInvestigatorId = "pi-100",
            principalInvestigatorName = "Dr. Smith",
            institution = "General Hospital",
            phase = "PHASE_1",
            status = "RECRUITING",
            enrollment = 1200.0,
            createdAt = OffsetDateTime.now().minusDays(5),
            updatedAt = OffsetDateTime.now()
        )
        val cachedResponse = ResearchQueryResponse(
            queryId = null,
            status = ResearchQueryStatus.COMPLETED,
            items = listOf(summary),
            pagination = PaginationMetadata.from(0, 10, 1),
            facets = mapOf("status" to listOf(FacetBucketResponse("RECRUITING", 1)))
        )
        val cachedJson = objectMapper.writeValueAsString(cachedResponse)

        `when`(cacheRepository.find(anyString())).thenReturn(Optional.of(cachedJson))

        val actual = sut.search(criteria)

        assertThat(actual.queryId).isEqualTo("query-123")
        assertThat(actual.items).containsExactly(summary)
        assertThat(actual.facets).containsKey("status")
        assertThat(meterRegistry.get("researchex.research.cache.hits").counter().count()).isEqualTo(1.0)
        val missCount = meterRegistry.find("researchex.research.cache.misses").counter()?.count() ?: 0.0
        assertThat(missCount).isEqualTo(0.0)

        verify(cacheRepository).find(anyString())
        verify(searchRepository, never()).search(any())
        verify(progressService).publishPending("tenant-alpha", "query-123", "검색 요청이 접수되었습니다.")
        verify(progressService).publishCompleted("tenant-alpha", "query-123", 1L, true, null)
        verify(progressService, never()).publishFailed(anyString(), anyString(), anyString(), anyString())
    }

    @Test
    fun searchQueriesRepositoryAndCachesWhenCacheMiss() {
        val criteria = ResearchQueryCriteria.builder()
            .query("cardiology")
            .tenantId("tenant-beta")
            .queryId("query-456")
            .page(1)
            .size(5)
            .build()

        val document = ResearchDocument(
            id = "doc-9",
            title = "심장 연구",
            summary = "샘플 요약",
            diseaseCodes = listOf("i10"),
            tags = listOf("cardio"),
            principalInvestigatorId = "pi-200",
            principalInvestigatorName = "Dr. Park",
            institution = "Heart Center",
            phase = "PHASE_2",
            status = "COMPLETED",
            enrollment = 800.0,
            createdAt = OffsetDateTime.now().minusDays(10),
            updatedAt = OffsetDateTime.now()
        )

        val searchResult = SearchResult(
            documents = listOf(document),
            totalHits = 1,
            facets = mapOf("status" to listOf(FacetBucket("COMPLETED", 1)))
        )

        `when`(cacheRepository.find(anyString())).thenReturn(Optional.empty())
        `when`(searchRepository.search(any())).thenReturn(searchResult)

        val response = sut.search(criteria)

        assertThat(response.status).isEqualTo(ResearchQueryStatus.COMPLETED)
        assertThat(response.items).hasSize(1)
        assertThat(response.queryId).isEqualTo("query-456")
        assertThat(meterRegistry.get("researchex.research.cache.misses").counter().count()).isEqualTo(1.0)

        verify(progressService).publishPending("tenant-beta", "query-456", "검색 요청이 접수되었습니다.")
        verify(progressService).publishRunning("tenant-beta", "query-456", 30.0, null, "Elasticsearch 검색을 실행합니다.")
        verify(progressService).publishCompleted("tenant-beta", "query-456", 1L, false, null)

        val cachePayloadCaptor = ArgumentCaptor.forClass(String::class.java)
        verify(cacheRepository).put(anyString(), cachePayloadCaptor.capture())

        val cachedPayload = objectMapper.readValue(cachePayloadCaptor.value, ResearchQueryResponse::class.java)
        assertThat(cachedPayload.queryId).isNull()
        assertThat(cachedPayload.items).hasSize(1)
    }

    @Test
    fun searchPublishesFailedAndRethrowsWhenRepositoryThrows() {
        val criteria = ResearchQueryCriteria.builder()
            .query("neurology")
            .tenantId("tenant-gamma")
            .queryId("query-789")
            .page(0)
            .size(10)
            .build()

        `when`(cacheRepository.find(anyString())).thenReturn(Optional.empty())
        `when`(searchRepository.search(any())).thenThrow(IllegalStateException("Elasticsearch timeout"))

        val exception = org.junit.jupiter.api.assertThrows<IllegalStateException> {
            sut.search(criteria)
        }

        assertThat(exception).hasMessageContaining("Elasticsearch timeout")
        verify(progressService).publishPending("tenant-gamma", "query-789", "검색 요청이 접수되었습니다.")
        verify(progressService).publishRunning("tenant-gamma", "query-789", 30.0, null, "Elasticsearch 검색을 실행합니다.")
        verify(progressService).publishFailed(eq("tenant-gamma"), eq("query-789"), eq("RESEARCH-SEARCH-ERROR"), anyString())
        verify(progressService, never()).publishCompleted(anyString(), anyString(), anyLong(), anyBoolean(), any())

        assertThat(meterRegistry.get("researchex.research.cache.misses").counter().count()).isEqualTo(1.0)
    }
}
