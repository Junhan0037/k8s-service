package com.researchex.research.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 메타데이터 엔드포인트는 게이트웨이/운영 도구에서 호출되는 공통 인터페이스이므로
 * 계약이 쉽게 깨지지 않도록 통합 테스트로 기본 동작을 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ServiceMetadataControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("메타데이터 엔드포인트는 서비스명과 설명을 반환한다")
    void metadataEndpointReturnsServicePayload() throws Exception {
        mockMvc.perform(get("/api/v1/metadata"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceName").value("research-service"))
                .andExpect(jsonPath("$.description").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }
}
