package com.researchex.research.service.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis Testcontainer를 사용해 다단 캐시 저장소의 동작을 통합 테스트한다.
 * 캐시 워밍업과 무효화 흐름을 통해 17단계 테스트 전략의 Redis 컨테이너 요구사항을 만족한다.
 */
@Testcontainers
class SearchResultCacheRepositoryTest {

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.2.4-alpine"))
                    .withExposedPorts(6379);

    private Cache<String, String> localCache;
    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private SearchResultCacheRepository repository;
    private String redisKeyPrefix;

    @BeforeEach
    void setUp() {
        this.localCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(100)
                .recordStats()
                .build();

        this.connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        this.connectionFactory.afterPropertiesSet();

        this.redisTemplate = new StringRedisTemplate(connectionFactory);
        this.redisTemplate.afterPropertiesSet();

        this.redisKeyPrefix = "integration::cache::";
        this.repository = new SearchResultCacheRepository(
                localCache,
                redisTemplate,
                redisKeyPrefix,
                60,
                TimeUnit.SECONDS
        );
    }

    @AfterEach
    void tearDown() {
        if (redisTemplate != null) {
            redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
        if (localCache != null) {
            localCache.cleanUp();
        }
    }

    @Test
    void putFindAndEvictShouldCoordinateLocalAndRedisCache() {
        String cacheKey = "query::tenantA::page0";
        String payload = "{\"result\":\"ok\"}";

        repository.put(cacheKey, payload);
        localCache.invalidate(cacheKey); // 로컬 캐시를 비워 Redis 워밍업을 검증한다.

        Optional<String> redisLoaded = repository.find(cacheKey);
        assertThat(redisLoaded).contains(payload);
        assertThat(localCache.getIfPresent(cacheKey)).isEqualTo(payload);

        // Redis 데이터를 삭제해도 로컬 캐시가 보조 스토리지로 동작하는지 확인한다.
        redisTemplate.delete(redisKeyPrefix + cacheKey);
        Optional<String> localFallback = repository.find(cacheKey);
        assertThat(localFallback).contains(payload);

        repository.evict(cacheKey);
        assertThat(repository.find(cacheKey)).isEmpty();
        assertThat(localCache.getIfPresent(cacheKey)).isNull();
    }
}
