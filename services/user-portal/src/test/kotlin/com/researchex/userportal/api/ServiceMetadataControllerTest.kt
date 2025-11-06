package com.researchex.userportal.api

import com.researchex.userportal.config.TestRedisConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.session.data.redis.RedisIndexedSessionRepository
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/** User Portal 서비스의 메타데이터 API 계약을 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestRedisConfiguration::class)
class ServiceMetadataControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val sessionRepository: RedisIndexedSessionRepository,
) {

    @MockBean
    private lateinit var redisMessageListenerContainer: RedisMessageListenerContainer

    @Test
    @DisplayName("User Portal 메타데이터 엔드포인트는 서비스명을 반환한다")
    fun metadataEndpointReturnsUserPortalInfo() {
        mockMvc.get("/api/v1/metadata")
            .andExpect {
                status { isOk() }
                jsonPath("$.serviceName") { value("user-portal") }
                jsonPath("$.description") { isNotEmpty() }
                jsonPath("$.timestamp") { isNotEmpty() }
            }
    }

    @Test
    @DisplayName("Redis 기반 Spring Session 저장소가 초기화된다")
    fun redisSessionRepositoryConfigured() {
        assertThat(sessionRepository).isNotNull
    }
}
