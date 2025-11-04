package com.researchex.research.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 메타데이터 엔드포인트는 게이트웨이/운영 도구에서 호출되는 공통 인터페이스이므로 계약이 쉽게 깨지지 않도록 통합 테스트로 기본 동작을 검증한다. */
@SpringBootTest(properties = "researchex.cache.enable-redis=false")
@AutoConfigureMockMvc
class ServiceMetadataControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  @DisplayName("메타데이터 엔드포인트는 서비스명과 설명을 반환한다")
  void metadataEndpointReturnsServicePayload() throws Exception {
    mockMvc
        .perform(get("/api/v1/metadata"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.serviceName").value("research-service"))
        .andExpect(jsonPath("$.description").isNotEmpty())
        .andExpect(jsonPath("$.timestamp").isNotEmpty());
  }

  @Test
  @DisplayName("메타데이터 캐시는 동일한 타임스탬프를 재사용해 불필요한 계산을 방지한다")
  void metadataEndpointUsesCache() throws Exception {
    String firstResponse =
        mockMvc.perform(get("/api/v1/metadata")).andReturn().getResponse().getContentAsString();
    String secondResponse =
        mockMvc.perform(get("/api/v1/metadata")).andReturn().getResponse().getContentAsString();

    ServiceMetadataResponse first =
        objectMapper.readValue(firstResponse, ServiceMetadataResponse.class);
    ServiceMetadataResponse second =
        objectMapper.readValue(secondResponse, ServiceMetadataResponse.class);

    assertThat(second.timestamp()).isEqualTo(first.timestamp());
  }
}
