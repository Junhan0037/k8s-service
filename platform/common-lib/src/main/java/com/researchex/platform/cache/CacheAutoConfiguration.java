package com.researchex.platform.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.lang.Nullable;

/**
 * 공통 모듈에서 제공하는 캐시 자동 구성. L1(Caffeine) + L2(Redis) 조합을 하나의 CacheManager 로 노출하여
 * 각 서비스가 캐시 전략을 일관되게 적용할 수 있도록 한다.
 */
@AutoConfiguration
@EnableCaching
@ConditionalOnClass(CacheManager.class)
@EnableConfigurationProperties(CacheProperties.class)
public class CacheAutoConfiguration {

  @Bean
  @Primary
  public CacheManager researchexCacheManager(CacheProperties properties, ObjectProvider<RedisConnectionFactory> connectionFactoryProvider, ObjectProvider<ObjectMapper> objectMapperProvider) {
    SimpleCacheManager caffeineCacheManager = createCaffeineCacheManager(properties);
    RedisConnectionFactory redisConnectionFactory = properties.isEnableRedis() ? connectionFactoryProvider.getIfAvailable() : null;
    CacheManager redisCacheManager = redisConnectionFactory != null ? createRedisCacheManager(redisConnectionFactory, properties, objectMapperProvider.getIfAvailable()) : null;
    return new MultiTierCacheManager(caffeineCacheManager, redisCacheManager);
  }

  private SimpleCacheManager createCaffeineCacheManager(CacheProperties properties) {
    List<CaffeineCache> caches = new ArrayList<>();
    caches.add(buildCaffeineCache(CacheNames.STATIC_REFERENCE, properties.getStaticTier(), properties.isCacheNullValues()));
    caches.add(buildCaffeineCache(CacheNames.DYNAMIC_QUERY, properties.getDynamicTier(), properties.isCacheNullValues()));

    SimpleCacheManager cacheManager = new SimpleCacheManager();
    cacheManager.setCaches(caches);
    cacheManager.afterPropertiesSet();
    return cacheManager;
  }

  private CaffeineCache buildCaffeineCache(String cacheName, CacheProperties.Tier tierProperties, boolean cacheNulls) {
    Caffeine<Object, Object> builder = Caffeine.newBuilder().expireAfterWrite(tierProperties.getTtl()).maximumSize(tierProperties.getMaximumSize());

    if (tierProperties.isRecordStats()) {
      builder.recordStats();
    }

    return new CaffeineCache(cacheName, builder.build(), cacheNulls);
  }

  private CacheManager createRedisCacheManager(RedisConnectionFactory connectionFactory, CacheProperties properties, @Nullable ObjectMapper objectMapper) {
    GenericJackson2JsonRedisSerializer valueSerializer = objectMapper != null ? new GenericJackson2JsonRedisSerializer(objectMapper) : new GenericJackson2JsonRedisSerializer();

    RedisCacheConfiguration baseConfig = RedisCacheConfiguration.defaultCacheConfig()
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer))
            .prefixCacheNameWith(properties.getRedisKeyPrefix());

    if (!properties.isCacheNullValues()) {
      baseConfig = baseConfig.disableCachingNullValues();
    }

    Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
    cacheConfigurations.put(CacheNames.STATIC_REFERENCE, baseConfig.entryTtl(properties.getStaticTier().getTtl()));
    cacheConfigurations.put(CacheNames.DYNAMIC_QUERY, baseConfig.entryTtl(properties.getDynamicTier().getTtl()));

    return RedisCacheManager.builder(connectionFactory)
        .cacheDefaults(baseConfig)
        .withInitialCacheConfigurations(cacheConfigurations)
        .transactionAware()
        .build();
  }
}
