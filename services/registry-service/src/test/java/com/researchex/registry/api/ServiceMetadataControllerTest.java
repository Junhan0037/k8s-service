package com.researchex.registry.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 메타데이터 API는 외부 도구에서 활용되므로 JSON 스키마가 안정적으로 반환되는지 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
class ServiceMetadataControllerTest {

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("Registry 메타데이터 엔드포인트는 서비스명을 포함한 응답을 제공한다")
  void metadataEndpointReturnsRegistryInfo() throws Exception {
    mockMvc
        .perform(get("/api/v1/metadata"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.serviceName").value("registry-service"))
        .andExpect(jsonPath("$.description").isNotEmpty())
        .andExpect(jsonPath("$.timestamp").isNotEmpty());
  }
}
