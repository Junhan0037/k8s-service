package com.researchex.research.service.cache

import com.github.benmanes.caffeine.cache.Cache
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.util.StringUtils
import java.util.Optional
import java.util.concurrent.TimeUnit

/**
 * 검색 결과 JSON을 로컬(Caffeine)과 Redis에 병기 저장해 재사용하는 저장소.
 * 캐시 키는 서비스 단에서 생성하며, TTL은 Redis에 위임한다.
 */
class SearchResultCacheRepository(
    private val localCache: Cache<String, String>,
    private val redisTemplate: StringRedisTemplate,
    private val redisKeyPrefix: String,
    private val ttl: Long,
    private val ttlUnit: TimeUnit
) {

    /**
     * 캐시에서 데이터를 조회한다. 로컬 캐시 미스 시 Redis에서 가져와 warm-up한다.
     *
     * @param key 캐시 키
     * @return 캐시된 JSON 페이로드
     */
    fun find(key: String): Optional<String> {
        val cached = localCache.getIfPresent(key)
        if (StringUtils.hasText(cached)) {
            return Optional.of(cached)
        }
        val redisValue = redisTemplate.opsForValue().get(prefixed(key))
        if (!StringUtils.hasText(redisValue)) {
            return Optional.empty()
        }
        localCache.put(key, redisValue)
        return Optional.of(redisValue!!)
    }

    /**
     * 검색 결과를 캐시에 저장한다. Redis TTL을 이용해 주기적으로 갱신한다.
     *
     * @param key     캐시 키
     * @param payload 직렬화된 검색 결과(JSON)
     */
    fun put(key: String, payload: String) {
        localCache.put(key, payload)
        redisTemplate.opsForValue().set(prefixed(key), payload, ttl, ttlUnit)
    }

    /**
     * 필요 시 수동으로 캐시를 무효화한다.
     *
     * @param key 캐시 키
     */
    fun evict(key: String) {
        localCache.invalidate(key)
        redisTemplate.delete(prefixed(key))
    }

    private fun prefixed(key: String): String = redisKeyPrefix + key
}
