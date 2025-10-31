package com.researchex.deid.api;

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
 * De-identification 서비스 메타데이터 계약을 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ServiceMetadataControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("De-identification 메타데이터 엔드포인트는 필수 정보를 제공한다")
    void metadataEndpointReturnsDeidInfo() throws Exception {
        mockMvc.perform(get("/api/v1/metadata"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceName").value("deid-service"))
                .andExpect(jsonPath("$.description").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }
}
