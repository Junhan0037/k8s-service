package com.researchex.platform.cache

import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.springframework.cache.support.AbstractCacheManager

/**
 * L1(LRU 기반 Caffeine)과 L2(Redis)를 결합하여 동작하는 CacheManager 구현체다.
 * Spring Cache 추상화는 복수 CacheManager를 동시에 지원하지 않으므로, 내부에서 두 캐시 매니저를 위임 받아 다단 캐시를 구성한다.
 */
class MultiTierCacheManager(
    private val l1CacheManager: CacheManager,
    private val l2CacheManager: CacheManager?,
    private val metricsRecorder: CacheMetricsRecorder
) : AbstractCacheManager() {

    override fun loadCaches(): Collection<Cache> {
        val cacheNames = LinkedHashSet<String>()
        cacheNames.addAll(l1CacheManager.cacheNames)
        if (l2CacheManager != null) {
            cacheNames.addAll(l2CacheManager.cacheNames)
        }
        return cacheNames.mapNotNull { name -> getMissingCache(name) }
    }

    override fun getMissingCache(name: String): Cache? {
        val l1 = l1CacheManager.getCache(name)
        val l2 = l2CacheManager?.getCache(name)
        if (l1 == null && l2 == null) {
            return null
        }
        return MultiTierCache(name, l1, l2, metricsRecorder)
    }
}
