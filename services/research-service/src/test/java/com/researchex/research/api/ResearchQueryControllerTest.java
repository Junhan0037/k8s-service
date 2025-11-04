package com.researchex.research.api;

import com.researchex.research.config.SearchProperties;
import com.researchex.research.service.ResearchQueryService;
import com.researchex.research.service.dto.PaginationMetadata;
import com.researchex.research.service.dto.ResearchQueryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ResearchQueryController} 파라미터 파싱 로직을 검증한다.
 */
class ResearchQueryControllerTest {

    @Mock
    private ResearchQueryService researchQueryService;

    private ResearchQueryController controller;
    private SearchProperties searchProperties;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        searchProperties = new SearchProperties();
        controller = new ResearchQueryController(researchQueryService, searchProperties);
    }

    @Test
    void search_parsesQueryAndSortsProperly() {
        ResearchQueryResponse stubResponse = new ResearchQueryResponse(List.of(), PaginationMetadata.from(0, 20, 0), Map.of());
        when(researchQueryService.search(any())).thenReturn(stubResponse);

        ResponseEntity<ResearchQueryResponse> responseEntity = controller.search(
                "chemotherapy",
                2,
                50,
                List.of("updatedAt:desc", "relevance:asc"),
                List.of("phase:PHASE_1,PHASE_2", "status:RECRUITING"),
                true
        );

        assertThat(responseEntity.getStatusCode().is2xxSuccessful()).isTrue();

        ArgumentCaptor<com.researchex.research.service.search.ResearchQueryCriteria> criteriaCaptor =
                ArgumentCaptor.forClass(com.researchex.research.service.search.ResearchQueryCriteria.class);
        verify(researchQueryService).search(criteriaCaptor.capture());

        com.researchex.research.service.search.ResearchQueryCriteria captured = criteriaCaptor.getValue();
        assertThat(captured.getQuery()).isEqualTo("chemotherapy");
        assertThat(captured.getPage()).isEqualTo(2);
        assertThat(captured.getSize()).isEqualTo(50);
        assertThat(captured.getSorts()).hasSize(2);
        assertThat(captured.getFilters()).containsKeys("phase", "status");
    }

    @Test
    void search_appliesDefaultPageSizeWhenMissing() {
        ResearchQueryResponse stubResponse = new ResearchQueryResponse(List.of(), PaginationMetadata.from(0, 20, 0), Map.of());
        when(researchQueryService.search(any())).thenReturn(stubResponse);

        controller.search("immune", 0, null, null, null, true);

        ArgumentCaptor<com.researchex.research.service.search.ResearchQueryCriteria> criteriaCaptor =
                ArgumentCaptor.forClass(com.researchex.research.service.search.ResearchQueryCriteria.class);
        verify(researchQueryService).search(criteriaCaptor.capture());

        com.researchex.research.service.search.ResearchQueryCriteria captured = criteriaCaptor.getValue();
        assertThat(captured.getSize()).isEqualTo(searchProperties.getDefaultPageSize());
    }
}
