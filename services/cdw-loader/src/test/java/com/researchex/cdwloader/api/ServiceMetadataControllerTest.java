package com.researchex.cdwloader.api;

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
 * CDW Loader 서비스 메타데이터 API가 안정적으로 동작하는지 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ServiceMetadataControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("CDW Loader 메타데이터 엔드포인트는 서비스 정보를 반환한다")
    void metadataEndpointReturnsCdwInfo() throws Exception {
        mockMvc.perform(get("/api/v1/metadata"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceName").value("cdw-loader"))
                .andExpect(jsonPath("$.description").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }
}
