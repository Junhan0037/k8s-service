package com.researchex.research.service.cache;

import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 검색 결과 JSON을 로컬(Caffeine)과 Redis에 병기 저장해 재사용하는 저장소.
 * 캐시 키는 서비스 단에서 생성하며, TTL은 Redis에 위임한다.
 */
public class SearchResultCacheRepository {

    private final Cache<String, String> localCache;
    private final StringRedisTemplate redisTemplate;
    private final String redisKeyPrefix;
    private final long ttl;
    private final TimeUnit ttlUnit;

    public SearchResultCacheRepository(
            Cache<String, String> localCache,
            StringRedisTemplate redisTemplate,
            String redisKeyPrefix,
            long ttl,
            TimeUnit ttlUnit
    ) {
        this.localCache = localCache;
        this.redisTemplate = redisTemplate;
        this.redisKeyPrefix = redisKeyPrefix;
        this.ttl = ttl;
        this.ttlUnit = ttlUnit;
    }

    /**
     * 캐시에서 데이터를 조회한다. 로컬 캐시 미스 시 Redis에서 가져와 warm-up한다.
     *
     * @param key 캐시 키
     * @return 캐시된 JSON 페이로드
     */
    public Optional<String> find(String key) {
        String cached = localCache.getIfPresent(key);
        if (StringUtils.hasText(cached)) {
            return Optional.of(cached);
        }
        String redisValue = redisTemplate.opsForValue().get(prefixed(key));
        if (!StringUtils.hasText(redisValue)) {
            return Optional.empty();
        }
        localCache.put(key, redisValue);
        return Optional.of(redisValue);
    }

    /**
     * 검색 결과를 캐시에 저장한다. Redis TTL을 이용해 주기적으로 갱신한다.
     *
     * @param key     캐시 키
     * @param payload 직렬화된 검색 결과(JSON)
     */
    public void put(String key, String payload) {
        localCache.put(key, payload);
        redisTemplate.opsForValue().set(prefixed(key), payload, ttl, ttlUnit);
    }

    /**
     * 필요 시 수동으로 캐시를 무효화한다.
     *
     * @param key 캐시 키
     */
    public void evict(String key) {
        localCache.invalidate(key);
        redisTemplate.delete(prefixed(key));
    }

    private String prefixed(String key) {
        return redisKeyPrefix + key;
    }
}
