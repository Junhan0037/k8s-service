package com.researchex.research.service.cache

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.util.Optional
import java.util.concurrent.TimeUnit

/**
 * Redis Testcontainer를 사용해 다단 캐시 저장소의 동작을 통합 테스트한다.
 * 캐시 워밍업과 무효화 흐름을 통해 17단계 테스트 전략의 Redis 컨테이너 요구사항을 만족한다.
 */
@Testcontainers
class SearchResultCacheRepositoryTest {

    private lateinit var localCache: Cache<String, String>
    private lateinit var connectionFactory: LettuceConnectionFactory
    private lateinit var redisTemplate: StringRedisTemplate
    private lateinit var repository: SearchResultCacheRepository
    private lateinit var redisKeyPrefix: String

    @BeforeEach
    fun setUp() {
        localCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(100)
            .recordStats()
            .build()

        connectionFactory = LettuceConnectionFactory(REDIS.host, REDIS.getMappedPort(6379)).apply {
            afterPropertiesSet()
        }

        redisTemplate = StringRedisTemplate(connectionFactory).apply {
            afterPropertiesSet()
        }

        redisKeyPrefix = "integration::cache::"
        repository = SearchResultCacheRepository(
            localCache = localCache,
            redisTemplate = redisTemplate,
            redisKeyPrefix = redisKeyPrefix,
            ttl = 60,
            ttlUnit = TimeUnit.SECONDS
        )
    }

    @AfterEach
    fun tearDown() {
        redisTemplate.connectionFactory?.connection?.serverCommands()?.flushDb()
        connectionFactory.destroy()
        localCache.cleanUp()
    }

    @Test
    fun putFindAndEvictShouldCoordinateLocalAndRedisCache() {
        val cacheKey = "query::tenantA::page0"
        val payload = """{"result":"ok"}"""

        repository.put(cacheKey, payload)
        localCache.invalidate(cacheKey) // 로컬 캐시를 비워 Redis 워밍업을 검증한다.

        val redisLoaded: Optional<String> = repository.find(cacheKey)
        assertThat(redisLoaded).contains(payload)
        assertThat(localCache.getIfPresent(cacheKey)).isEqualTo(payload)

        redisTemplate.delete(redisKeyPrefix + cacheKey)
        val localFallback = repository.find(cacheKey)
        assertThat(localFallback).contains(payload)

        repository.evict(cacheKey)
        assertThat(repository.find(cacheKey)).isEmpty
        assertThat(localCache.getIfPresent(cacheKey)).isNull()
    }

    companion object {
        @Container
        @JvmStatic
        private val REDIS: GenericContainer<*> = GenericContainer(DockerImageName.parse("redis:7.2.4-alpine"))
            .withExposedPorts(6379)
    }
}
