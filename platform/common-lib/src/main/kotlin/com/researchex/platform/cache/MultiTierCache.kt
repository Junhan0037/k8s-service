package com.researchex.platform.cache

import org.springframework.cache.Cache
import org.springframework.cache.support.SimpleValueWrapper
import java.util.concurrent.Callable

/**
 * Caffeine(L1)과 Redis(L2)를 묶어 일관된 캐시 동작을 제공하는 Cache 구현체다.
 * L1 조회 실패 시 L2에서 데이터를 가져와 L1에 재적재하고, 쓰기 연산은 두 계층에 모두 반영한다.
 * 단일 책임을 유지하기 위해 동기화 전략은 Spring Cache 추상화에 위임한다.
 */
class MultiTierCache(
    private val name: String,
    private val l1Cache: Cache?,
    private val l2Cache: Cache?,
    private val metricsRecorder: CacheMetricsRecorder
) : Cache {

    override fun getName(): String = name

    override fun getNativeCache(): Any {
        return arrayOf(l1Cache?.nativeCache, l2Cache?.nativeCache)
    }

    override fun get(key: Any): Cache.ValueWrapper? {
        val valueFromL1 = getFromCache(l1Cache, key)
        if (valueFromL1 != null) {
            metricsRecorder.recordL1Hit(name)
            return valueFromL1
        }

        val valueFromL2 = getFromCache(l2Cache, key)
        if (valueFromL2 != null && l1Cache != null) {
            metricsRecorder.recordL2Hit(name)
            l1Cache.put(key, valueFromL2.get())
        }
        if (valueFromL2 == null) {
            metricsRecorder.recordMiss(name)
        }
        return valueFromL2
    }

    override fun <T : Any?> get(key: Any, type: Class<T>?): T? {
        val wrapper = get(key) ?: return null
        val value = wrapper.get()
        if (type != null && !type.isInstance(value)) {
            throw IllegalStateException("Cached value is not of required type: ${type.name}, actual: ${value?.javaClass?.name}")
        }
        @Suppress("UNCHECKED_CAST")
        return value as T?
    }

    override fun <T : Any?> get(key: Any, valueLoader: Callable<T>): T {
        val wrapper = get(key)
        if (wrapper != null) {
            @Suppress("UNCHECKED_CAST")
            return wrapper.get() as T
        }
        return try {
            val value = valueLoader.call()
            put(key, value)
            value
        } catch (ex: Exception) {
            throw Cache.ValueRetrievalException(key, valueLoader, ex)
        }
    }

    override fun put(key: Any, value: Any?) {
        l1Cache?.put(key, value)
        l2Cache?.put(key, value)
    }

    override fun putIfAbsent(key: Any, value: Any?): Cache.ValueWrapper? {
        val currentL1 = l1Cache?.putIfAbsent(key, value)
        val currentL2 = l2Cache?.putIfAbsent(key, value)

        if (currentL1 != null) {
            return currentL1
        }
        if (currentL2 != null) {
            if (l1Cache != null) {
                l1Cache.put(key, currentL2.get())
            }
            return currentL2
        }

        return value?.let { SimpleValueWrapper(it) }
    }

    override fun evict(key: Any) {
        l1Cache?.evict(key)
        l2Cache?.evict(key)
    }

    override fun clear() {
        l1Cache?.clear()
        l2Cache?.clear()
    }

    private fun getFromCache(cache: Cache?, key: Any): Cache.ValueWrapper? {
        return cache?.get(key)
    }
}
