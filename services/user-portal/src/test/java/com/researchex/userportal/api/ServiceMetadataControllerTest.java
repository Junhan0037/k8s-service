package com.researchex.userportal.api;

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
 * User Portal 서비스의 메타데이터 API 계약을 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ServiceMetadataControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("User Portal 메타데이터 엔드포인트는 서비스명을 반환한다")
    void metadataEndpointReturnsUserPortalInfo() throws Exception {
        mockMvc.perform(get("/api/v1/metadata"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceName").value("user-portal"))
                .andExpect(jsonPath("$.description").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }
}
