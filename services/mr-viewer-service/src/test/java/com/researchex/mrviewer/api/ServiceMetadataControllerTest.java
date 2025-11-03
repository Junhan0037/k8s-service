package com.researchex.mrviewer.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** MR Viewer 서비스 메타데이터 API가 정상적으로 노출되는지 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
class ServiceMetadataControllerTest {

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("MR Viewer 메타데이터 엔드포인트는 필수 필드를 반환한다")
  void metadataEndpointReturnsPayload() throws Exception {
    mockMvc
        .perform(get("/api/v1/metadata"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.serviceName").value("mr-viewer-service"))
        .andExpect(jsonPath("$.description").isNotEmpty())
        .andExpect(jsonPath("$.timestamp").isNotEmpty());
  }
}
