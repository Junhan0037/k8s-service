package com.researchex.userportal.config

import org.mockito.Answers
import org.mockito.Mockito.mock
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.connection.RedisConnectionFactory

/**
 * 통합 테스트에서는 실제 Redis 인스턴스를 띄우지 않으므로, RedisConnectionFactory를 Mockito 기반
 * 더블로 대체한다. Spring Session은 ConnectionFactory만 존재하면 초기화되므로, 딥 스텁을 활용해
 * 내부 호출에서 NPE가 발생하지 않도록 구성한다.
 */
@TestConfiguration
class TestRedisConfiguration {

    @Bean
    fun redisConnectionFactory(): RedisConnectionFactory {
        return mock(RedisConnectionFactory::class.java, Answers.RETURNS_DEEP_STUBS)
    }
}
