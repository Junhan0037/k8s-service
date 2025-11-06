package com.researchex.research.api

import com.researchex.contract.research.ResearchQueryStatus
import com.researchex.research.config.SearchProperties
import com.researchex.research.service.ResearchQueryService
import com.researchex.research.service.dto.PaginationMetadata
import com.researchex.research.service.dto.ResearchQueryResponse
import com.researchex.research.service.search.ResearchQueryCriteria
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.any
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.springframework.http.ResponseEntity

/**
 * [ResearchQueryController] 파라미터 파싱 로직을 검증한다.
 */
class ResearchQueryControllerTest {

    @Mock
    private lateinit var researchQueryService: ResearchQueryService

    private lateinit var controller: ResearchQueryController
    private lateinit var searchProperties: SearchProperties

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        searchProperties = SearchProperties()
        controller = ResearchQueryController(researchQueryService, searchProperties)
    }

    @Test
    fun searchParsesQueryAndSortsProperly() {
        val stubResponse = ResearchQueryResponse(
            queryId = "query-1",
            status = ResearchQueryStatus.COMPLETED,
            items = emptyList(),
            pagination = PaginationMetadata.from(0, 20, 0),
            facets = emptyMap()
        )
        `when`(researchQueryService.search(any(ResearchQueryCriteria::class.java))).thenReturn(stubResponse)

        val responseEntity: ResponseEntity<ResearchQueryResponse> = controller.search(
            query = "chemotherapy",
            page = 2,
            size = 50,
            sortParams = listOf("updatedAt:desc", "relevance:asc"),
            filterParams = listOf("phase:PHASE_1,PHASE_2", "status:RECRUITING"),
            useCache = true,
            queryId = "query-1",
            tenantId = "tenant-blue"
        )

        assertThat(responseEntity.statusCode.is2xxSuccessful).isTrue()

        val criteriaCaptor = ArgumentCaptor.forClass(ResearchQueryCriteria::class.java)
        verify(researchQueryService).search(criteriaCaptor.capture())

        val captured = criteriaCaptor.value
        assertThat(captured.query).isEqualTo("chemotherapy")
        assertThat(captured.page).isEqualTo(2)
        assertThat(captured.size).isEqualTo(50)
        assertThat(captured.sorts).hasSize(2)
        assertThat(captured.filters).containsKeys("phase", "status")
        assertThat(captured.tenantId).isEqualTo("tenant-blue")
        assertThat(captured.queryId).isEqualTo("query-1")
    }

    @Test
    fun searchAppliesDefaultPageSizeWhenMissing() {
        val stubResponse = ResearchQueryResponse(
            queryId = "query-2",
            status = ResearchQueryStatus.COMPLETED,
            items = emptyList(),
            pagination = PaginationMetadata.from(0, 20, 0),
            facets = emptyMap()
        )
        `when`(researchQueryService.search(any(ResearchQueryCriteria::class.java))).thenReturn(stubResponse)

        controller.search(
            query = "immune",
            page = 0,
            size = null,
            sortParams = null,
            filterParams = null,
            useCache = true,
            queryId = null,
            tenantId = null
        )

        val criteriaCaptor = ArgumentCaptor.forClass(ResearchQueryCriteria::class.java)
        verify(researchQueryService).search(criteriaCaptor.capture())

        val captured = criteriaCaptor.value
        assertThat(captured.size).isEqualTo(searchProperties.defaultPageSize)
        assertThat(captured.tenantId).isEqualTo("default")
        assertThat(captured.queryId).isNotNull()
    }
}
