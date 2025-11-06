package com.researchex.mrviewer.api

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/** MR Viewer 서비스 메타데이터 API가 정상적으로 노출되는지 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
class ServiceMetadataControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
) {

    @Test
    @DisplayName("MR Viewer 메타데이터 엔드포인트는 필수 필드를 반환한다")
    fun metadataEndpointReturnsPayload() {
        mockMvc.get("/api/v1/metadata")
            .andExpect {
                status { isOk() }
                jsonPath("$.serviceName") { value("mr-viewer-service") }
                jsonPath("$.description") { isNotEmpty() }
                jsonPath("$.timestamp") { isNotEmpty() }
            }
    }
}
