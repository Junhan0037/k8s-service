package com.researchex.cdwloader.api

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/** CDW Loader 서비스 메타데이터 API가 안정적으로 동작하는지 확인한다. */
@SpringBootTest
@AutoConfigureMockMvc
class ServiceMetadataControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
) {

    @Test
    @DisplayName("CDW Loader 메타데이터 엔드포인트는 서비스 정보를 반환한다")
    fun metadataEndpointReturnsCdwInfo() {
        mockMvc.get("/api/v1/metadata")
            .andExpect {
                status { isOk() }
                jsonPath("$.serviceName") { value("cdw-loader") }
                jsonPath("$.description") { isNotEmpty() }
                jsonPath("$.timestamp") { isNotEmpty() }
            }
    }
}
