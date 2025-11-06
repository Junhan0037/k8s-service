package com.researchex.platform.cache

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.benmanes.caffeine.cache.Caffeine
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCache
import org.springframework.cache.support.SimpleCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext

/**
 * 공통 모듈에서 제공하는 캐시 자동 구성이다. L1(Caffeine) + L2(Redis) 조합을 하나의 CacheManager 로 노출하여
 * 각 서비스가 캐시 전략을 일관되게 적용할 수 있도록 한다.
 */
@AutoConfiguration
@EnableCaching
@ConditionalOnClass(CacheManager::class)
@EnableConfigurationProperties(CacheProperties::class)
class CacheAutoConfiguration {

    @Bean
    @Primary
    fun researchexCacheManager(
        properties: CacheProperties,
        connectionFactoryProvider: ObjectProvider<RedisConnectionFactory>,
        objectMapperProvider: ObjectProvider<ObjectMapper>,
        meterRegistryProvider: ObjectProvider<MeterRegistry>
    ): CacheManager {
        val meterRegistry = meterRegistryProvider.getIfAvailable()
        val caffeineCacheManager = createCaffeineCacheManager(properties, meterRegistry)
        val redisConnectionFactory = if (properties.enableRedis) connectionFactoryProvider.getIfAvailable() else null
        val redisCacheManager = redisConnectionFactory?.let {
            createRedisCacheManager(it, properties, objectMapperProvider.getIfAvailable())
        }
        val metricsRecorder = meterRegistry?.let { CacheMetricsRecorder.instrumented(it) } ?: CacheMetricsRecorder.noop()
        return MultiTierCacheManager(caffeineCacheManager, redisCacheManager, metricsRecorder)
    }

    private fun createCaffeineCacheManager(
        properties: CacheProperties,
        meterRegistry: MeterRegistry?
    ): SimpleCacheManager {
        val caches = listOf(
            buildCaffeineCache(CacheNames.STATIC_REFERENCE, properties.staticTier, properties.cacheNullValues),
            buildCaffeineCache(CacheNames.DYNAMIC_QUERY, properties.dynamicTier, properties.cacheNullValues)
        )

        return SimpleCacheManager().apply {
            setCaches(caches)
            afterPropertiesSet()
            if (meterRegistry != null) {
                registerCaffeineMetrics(this, meterRegistry)
            }
        }
    }

    private fun buildCaffeineCache(
        cacheName: String,
        tierProperties: CacheProperties.Tier,
        cacheNulls: Boolean
    ): CaffeineCache {
        val builder = Caffeine.newBuilder()
            .expireAfterWrite(tierProperties.ttl)
            .maximumSize(tierProperties.maximumSize)

        if (tierProperties.recordStats) {
            builder.recordStats()
        }
        return CaffeineCache(cacheName, builder.build<Any, Any>(), cacheNulls)
    }

    private fun createRedisCacheManager(
        connectionFactory: RedisConnectionFactory,
        properties: CacheProperties,
        objectMapper: ObjectMapper?
    ): CacheManager {
        val valueSerializer = objectMapper?.let { GenericJackson2JsonRedisSerializer(it) }
            ?: GenericJackson2JsonRedisSerializer()

        var baseConfig = RedisCacheConfiguration.defaultCacheConfig()
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer))
            .prefixCacheNameWith(properties.redisKeyPrefix)

        if (!properties.cacheNullValues) {
            baseConfig = baseConfig.disableCachingNullValues()
        }

        val cacheConfigurations = mapOf(
            CacheNames.STATIC_REFERENCE to baseConfig.entryTtl(properties.staticTier.ttl),
            CacheNames.DYNAMIC_QUERY to baseConfig.entryTtl(properties.dynamicTier.ttl)
        )

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(baseConfig)
            .withInitialCacheConfigurations(cacheConfigurations)
            .transactionAware()
            .build()
    }

    private fun registerCaffeineMetrics(cacheManager: SimpleCacheManager, meterRegistry: MeterRegistry) {
        cacheManager.cacheNames.forEach { cacheName ->
            val cache: Cache? = cacheManager.getCache(cacheName)
            if (cache is CaffeineCache) {
                CaffeineCacheMetrics.monitor(
                    meterRegistry,
                    cache.nativeCache,
                    "researchex.cache.l1",
                    "cache",
                    cacheName
                )
            }
        }
    }
}
