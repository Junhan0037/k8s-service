package com.researchex.userportal.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.test.web.servlet.MockMvc;

import com.researchex.userportal.config.TestRedisConfiguration;

/** User Portal 서비스의 메타데이터 API 계약을 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestRedisConfiguration.class)
class ServiceMetadataControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private RedisIndexedSessionRepository sessionRepository;
  @MockBean private RedisMessageListenerContainer redisMessageListenerContainer;

  @Test
  @DisplayName("User Portal 메타데이터 엔드포인트는 서비스명을 반환한다")
  void metadataEndpointReturnsUserPortalInfo() throws Exception {
    mockMvc
        .perform(get("/api/v1/metadata"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.serviceName").value("user-portal"))
        .andExpect(jsonPath("$.description").isNotEmpty())
        .andExpect(jsonPath("$.timestamp").isNotEmpty());
  }

  @Test
  @DisplayName("Redis 기반 Spring Session 저장소가 초기화된다")
  void redisSessionRepositoryConfigured() {
    assertThat(sessionRepository).isNotNull();
  }
}
