package com.researchex.registry.api

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/** 메타데이터 API는 외부 도구에서 활용되므로 JSON 스키마가 안정적으로 반환되는지 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
class ServiceMetadataControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
) {

    @Test
    @DisplayName("Registry 메타데이터 엔드포인트는 서비스명을 포함한 응답을 제공한다")
    fun metadataEndpointReturnsRegistryInfo() {
        mockMvc.get("/api/v1/metadata")
            .andExpect {
                status { isOk() }
                jsonPath("$.serviceName") { value("registry-service") }
                jsonPath("$.description") { isNotEmpty() }
                jsonPath("$.timestamp") { isNotEmpty() }
            }
    }
}
