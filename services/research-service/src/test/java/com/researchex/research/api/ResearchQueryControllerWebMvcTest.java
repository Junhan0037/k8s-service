package com.researchex.research.api;

import com.researchex.contract.research.ResearchQueryStatus;
import com.researchex.research.config.SearchProperties;
import com.researchex.research.service.ResearchQueryService;
import com.researchex.research.service.dto.PaginationMetadata;
import com.researchex.research.service.dto.ResearchQueryResponse;
import com.researchex.research.service.search.ResearchQueryCriteria;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link ResearchQueryController}의 파라미터 파싱과 서비스 위임을 검증하는 WebMvc 슬라이스 테스트.
 * 컨트롤러 계층만 로드해 요청 파라미터 → Criteria 변환이 올바른지 확인한다.
 */
@WebMvcTest(controllers = ResearchQueryController.class)
@Import(ResearchQueryControllerWebMvcTest.TestConfig.class)
class ResearchQueryControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ResearchQueryService researchQueryService;

    @Test
    void searchEndpointParsesQueryParametersCorrectly() throws Exception {
        ResearchQueryResponse response = new ResearchQueryResponse(
                "query-xyz",
                ResearchQueryStatus.COMPLETED,
                List.of(),
                PaginationMetadata.from(0, 15, 0),
                Map.of()
        );
        when(researchQueryService.search(any())).thenReturn(response);

        mockMvc.perform(get("/api/research/search")
                        .param("query", "cancer")
                        .param("page", "1")
                        .param("size", "15")
                        .param("sort", "updatedAt:asc", "enrollment:desc")
                        .param("filter", "status:RECRUITING,ACTIVE")
                        .param("filter", "phase:PHASE_1")
                        .param("cache", "false")
                        .param("tenantId", "tenant-1")
                        .param("queryId", "query-xyz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queryId").value("query-xyz"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        ArgumentCaptor<ResearchQueryCriteria> criteriaCaptor = ArgumentCaptor.forClass(ResearchQueryCriteria.class);
        verify(researchQueryService).search(criteriaCaptor.capture());

        ResearchQueryCriteria criteria = criteriaCaptor.getValue();
        assertThat(criteria.getTenantId()).isEqualTo("tenant-1");
        assertThat(criteria.getQuery()).isEqualTo("cancer");
        assertThat(criteria.getPage()).isEqualTo(1);
        assertThat(criteria.getSize()).isEqualTo(15);
        assertThat(criteria.isUseCache()).isFalse();
        assertThat(criteria.getFilters().get("status")).containsExactly("RECRUITING", "ACTIVE");
        assertThat(criteria.getFilters().get("phase")).containsExactly("PHASE_1");
        assertThat(criteria.getSorts()).hasSize(2);
    }

    /**
     * WebMvcTest 슬라이스에 필요한 설정 빈(SearchProperties)을 수동으로 등록한다.
     */
    @TestConfiguration
    static class TestConfig {
        @Bean
        SearchProperties searchProperties() {
            return new SearchProperties();
        }
    }
}
