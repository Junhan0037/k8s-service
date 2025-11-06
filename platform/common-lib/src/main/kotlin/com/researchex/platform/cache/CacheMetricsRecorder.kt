package com.researchex.platform.cache

import io.micrometer.core.instrument.FunctionCounter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 다단 캐시(L1/L2) 조회 결과를 관측 지표로 노출하는 헬퍼다.
 * Micrometer 레지스트리가 존재하는 경우에만 계측을 수행하며, 서비스별로 동일한 메트릭 이름을 유지해 Grafana 대시보드에서 일관된 패널 구성이 가능하도록 한다.
 */
class CacheMetricsRecorder private constructor(
    private val meterRegistry: MeterRegistry?
) {

    private val holders: MutableMap<String, CacheMetricHolder> = ConcurrentHashMap()

    /** 메트릭을 기록하지 않는 No-op 인스턴스를 반환한다. */
    companion object {
        fun noop(): CacheMetricsRecorder = CacheMetricsRecorder(null)

        /** MeterRegistry 가 존재한다면 실측을 수행하는 Recorder 를 생성한다. */
        fun instrumented(meterRegistry: MeterRegistry): CacheMetricsRecorder = CacheMetricsRecorder(meterRegistry)
    }

    /** L1(Caffeine) 캐시 히트를 기록한다. */
    fun recordL1Hit(cacheName: String) {
        resolveHolder(cacheName)?.l1Hits?.incrementAndGet()
    }

    /** L2(Redis) 캐시 히트를 기록한다. */
    fun recordL2Hit(cacheName: String) {
        resolveHolder(cacheName)?.l2Hits?.incrementAndGet()
    }

    /** 캐시 미스를 기록한다. */
    fun recordMiss(cacheName: String) {
        resolveHolder(cacheName)?.misses?.incrementAndGet()
    }

    private fun resolveHolder(cacheName: String): CacheMetricHolder? {
        if (meterRegistry == null) {
            return null
        }
        return holders.computeIfAbsent(cacheName, ::registerMeters)
    }

    private fun registerMeters(cacheName: String): CacheMetricHolder {
        val holder = CacheMetricHolder()

        FunctionCounter.builder("researchex.cache.hit.count", holder, CacheMetricHolder::totalHits)
            .description("다단 캐시 누적 히트 건수(L1 + L2)")
            .tag("cache", cacheName)
            .register(meterRegistry)

        FunctionCounter.builder("researchex.cache.hit.l1.count", holder, CacheMetricHolder::l1Hits)
            .description("L1(Caffeine) 캐시 누적 히트 건수")
            .tag("cache", cacheName)
            .register(meterRegistry)

        FunctionCounter.builder("researchex.cache.hit.l2.count", holder, CacheMetricHolder::l2Hits)
            .description("L2(Redis) 캐시 누적 히트 건수")
            .tag("cache", cacheName)
            .register(meterRegistry)

        FunctionCounter.builder("researchex.cache.miss.count", holder, CacheMetricHolder::misses)
            .description("다단 캐시 누적 미스 건수")
            .tag("cache", cacheName)
            .register(meterRegistry)

        Gauge.builder("researchex.cache.hit.ratio", holder, CacheMetricHolder::hitRatio)
            .description("다단 캐시 전체 히트율(L1+L2 / 전체 요청)")
            .tag("cache", cacheName)
            .register(meterRegistry)

        Gauge.builder("researchex.cache.l2.dependency", holder, CacheMetricHolder::l2DependencyRatio)
            .description("전체 히트 중 L2(Redis)가 차지하는 비율")
            .tag("cache", cacheName)
            .register(meterRegistry)

        return holder
    }

    /**
     * AtomicLong 기반으로 누적된 메트릭 값을 보관한다.
     * FunctionCounter/Gauge가 참조하도록 전달되며, 추후 Garbage Collection으로 해제되지 않도록 strong reference를 유지한다.
     */
    private class CacheMetricHolder {
        val l1Hits: AtomicLong = AtomicLong()
        val l2Hits: AtomicLong = AtomicLong()
        val misses: AtomicLong = AtomicLong()

        fun l1Hits(): Double = l1Hits.toDouble()
        fun l2Hits(): Double = l2Hits.toDouble()
        fun misses(): Double = misses.toDouble()
        fun totalHits(): Double = l1Hits() + l2Hits()
        private fun totalRequests(): Double = totalHits() + misses()

        fun hitRatio(): Double {
            val total = totalRequests()
            return if (total == 0.0) 1.0 else totalHits() / total
        }

        fun l2DependencyRatio(): Double {
            val totalHit = totalHits()
            return if (totalHit == 0.0) 0.0 else l2Hits() / totalHit
        }
    }
}
