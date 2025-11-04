package com.researchex.research.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.researchex.research.service.cache.SearchResultCacheRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 검색 결과에 대한 다단 캐시 구성(Caffeine 1차 + Redis 2차)을 책임진다.
 */
@Configuration
public class ResearchCacheConfig {

    /**
     * 동적 검색 결과를 저장하는 로컬(메모리) 캐시 인스턴스를 생성한다.
     *
     * @param properties 캐시 TTL/사이즈 정책
     * @return Caffeine 기반 캐시
     */
    @Bean
    public Cache<String, String> researchSearchLocalCache(SearchCacheProperties properties) {
        Duration ttl = properties.getDynamicTier().ttl();
        return Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(properties.getDynamicTier().maximumSize())
                .recordStats()
                .build();
    }

    /**
     * Redis와 JSON 직렬화를 활용해 검색 결과를 보관할 템플릿을 구성한다.
     *
     * @param connectionFactory Redis 연결 팩토리
     * @return 문자열 기반 Redis 템플릿
     */
    @Bean
    public StringRedisTemplate researchSearchRedisTemplate(RedisConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        template.setDefaultSerializer(RedisSerializer.string()); // // UTF-8 문자열 직렬화를 명시해 서비스 간 호환성을 보장한다.
        return template;
    }

    /**
     * 캐시 저장소(로컬 + Redis)를 실제 검색 서비스에서 쉽게 사용할 수 있도록 래핑한다.
     *
     * @param localCache     1차 캐시
     * @param redisTemplate  2차 캐시
     * @param properties     캐시 정책
     * @return 검색 결과 캐시 저장소
     */
    @Bean
    public SearchResultCacheRepository searchResultCacheRepository(Cache<String, String> localCache, StringRedisTemplate redisTemplate, SearchCacheProperties properties) {
        long ttlSeconds = properties.getDynamicTier().ttl().toSeconds();

        return new SearchResultCacheRepository(
                localCache,
                redisTemplate,
                properties.getRedisKeyPrefix(),
                ttlSeconds == 0 ? 1 : ttlSeconds,
                TimeUnit.SECONDS
        );
    }
}
