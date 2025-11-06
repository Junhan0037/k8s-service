package com.researchex.research.config

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.researchex.research.service.cache.SearchResultCacheRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.serializer.RedisSerializer
import java.util.concurrent.TimeUnit

/**
 * 검색 결과에 대한 다단 캐시 구성(Caffeine 1차 + Redis 2차)을 책임진다.
 */
@Configuration
class ResearchCacheConfig {

    /**
     * 동적 검색 결과를 저장하는 로컬(메모리) 캐시 인스턴스를 생성한다.
     */
    @Bean
    fun researchSearchLocalCache(properties: SearchCacheProperties): Cache<String, String> {
        val ttl = properties.dynamicTier.ttl
        return Caffeine.newBuilder()
            .expireAfterWrite(ttl)
            .maximumSize(properties.dynamicTier.maximumSize)
            .recordStats()
            .build()
    }

    /**
     * Redis와 JSON 직렬화를 활용해 검색 결과를 보관할 템플릿을 구성한다.
     */
    @Bean
    fun researchSearchRedisTemplate(connectionFactory: RedisConnectionFactory): StringRedisTemplate {
        return StringRedisTemplate(connectionFactory).apply {
            setDefaultSerializer(RedisSerializer.string())
        }
    }

    /**
     * 캐시 저장소(로컬 + Redis)를 실제 검색 서비스에서 쉽게 사용할 수 있도록 래핑한다.
     */
    @Bean
    fun searchResultCacheRepository(
        localCache: Cache<String, String>,
        redisTemplate: StringRedisTemplate,
        properties: SearchCacheProperties
    ): SearchResultCacheRepository {
        val ttlSeconds = properties.dynamicTier.ttl.seconds
        val resolvedTtl = if (ttlSeconds == 0L) 1L else ttlSeconds
        return SearchResultCacheRepository(
            localCache,
            redisTemplate,
            properties.redisKeyPrefix,
            resolvedTtl,
            TimeUnit.SECONDS
        )
    }
}
