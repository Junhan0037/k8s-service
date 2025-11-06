package com.researchex.deid.api

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/** De-identification 서비스 메타데이터 계약을 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
class ServiceMetadataControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
) {

    @Test
    @DisplayName("De-identification 메타데이터 엔드포인트는 필수 정보를 제공한다")
    fun metadataEndpointReturnsDeidInfo() {
        mockMvc.get("/api/v1/metadata")
            .andExpect {
                status { isOk() }
                jsonPath("$.serviceName") { value("deid-service") }
                jsonPath("$.description") { isNotEmpty() }
                jsonPath("$.timestamp") { isNotEmpty() }
            }
    }
}
