package com.researchex.platform.cache;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.FunctionCounter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.lang.Nullable;

/**
 * 다단 캐시(L1/L2) 조회 결과를 관측 지표로 노출하는 헬퍼.
 * Micrometer 레지스트리가 존재하는 경우에만 계측을 수행하며, 서비스별로 동일한 메트릭 이름을 유지해 Grafana 대시보드에서 일관된 패널 구성이 가능하도록 한다.
 */
public final class CacheMetricsRecorder {

  private final MeterRegistry meterRegistry;
  private final Map<String, CacheMetricHolder> holders = new ConcurrentHashMap<>();

  private CacheMetricsRecorder(@Nullable MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  /** 메트릭을 기록하지 않는 No-op 인스턴스를 반환한다. */
  public static CacheMetricsRecorder noop() {
    return new CacheMetricsRecorder(null);
  }

  /** MeterRegistry 가 존재한다면 실측을 수행하는 Recorder 를 생성한다. */
  public static CacheMetricsRecorder instrumented(MeterRegistry meterRegistry) {
    return new CacheMetricsRecorder(meterRegistry);
  }

  /** L1(Caffeine) 캐시 히트를 기록한다. */
  public void recordL1Hit(String cacheName) {
    CacheMetricHolder holder = resolveHolder(cacheName);
    if (holder != null) {
      holder.l1Hits.incrementAndGet();
    }
  }

  /** L2(Redis) 캐시 히트를 기록한다. */
  public void recordL2Hit(String cacheName) {
    CacheMetricHolder holder = resolveHolder(cacheName);
    if (holder != null) {
      holder.l2Hits.incrementAndGet();
    }
  }

  /** 캐시 미스를 기록한다. */
  public void recordMiss(String cacheName) {
    CacheMetricHolder holder = resolveHolder(cacheName);
    if (holder != null) {
      holder.misses.incrementAndGet();
    }
  }

  @Nullable
  private CacheMetricHolder resolveHolder(String cacheName) {
    if (meterRegistry == null) {
      return null;
    }
    return holders.computeIfAbsent(cacheName, this::registerMeters);
  }

  private CacheMetricHolder registerMeters(String cacheName) {
    CacheMetricHolder holder = new CacheMetricHolder();

    // 캐시 계층별 누적 카운터를 FunctionCounter 로 노출한다.
    FunctionCounter.builder("researchex.cache.hit.count", holder, CacheMetricHolder::totalHits)
        .description("다단 캐시 누적 히트 건수(L1 + L2)")
        .tag("cache", cacheName)
        .register(meterRegistry);

    FunctionCounter.builder("researchex.cache.hit.l1.count", holder, CacheMetricHolder::l1Hits)
        .description("L1(Caffeine) 캐시 누적 히트 건수")
        .tag("cache", cacheName)
        .register(meterRegistry);

    FunctionCounter.builder("researchex.cache.hit.l2.count", holder, CacheMetricHolder::l2Hits)
        .description("L2(Redis) 캐시 누적 히트 건수")
        .tag("cache", cacheName)
        .register(meterRegistry);

    FunctionCounter.builder("researchex.cache.miss.count", holder, CacheMetricHolder::misses)
        .description("다단 캐시 누적 미스 건수")
        .tag("cache", cacheName)
        .register(meterRegistry);

    // 히트율과 L2 의존도를 Gauge 로 계산해 모니터링한다.
    Gauge.builder("researchex.cache.hit.ratio", holder, CacheMetricHolder::hitRatio)
        .description("다단 캐시 전체 히트율(L1+L2 / 전체 요청)")
        .tag("cache", cacheName)
        .register(meterRegistry);

    Gauge.builder("researchex.cache.l2.dependency", holder, CacheMetricHolder::l2DependencyRatio)
        .description("전체 히트 중 L2(Redis)가 차지하는 비율")
        .tag("cache", cacheName)
        .register(meterRegistry);

    return holder;
  }

  /**
   * AtomicLong 기반으로 누적된 메트릭 값을 보관한다. AtomicLong은 FunctionCounter/Gauge가 참조하도록 전달되며, 추후 Garbage Collection으로 해제되지 않도록 strong reference를 유지한다.
   */
  private static final class CacheMetricHolder {
    private final AtomicLong l1Hits = new AtomicLong();
    private final AtomicLong l2Hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();

    double l1Hits() {
      return l1Hits.doubleValue();
    }

    double l2Hits() {
      return l2Hits.doubleValue();
    }

    double misses() {
      return misses.doubleValue();
    }

    double totalHits() {
      return l1Hits() + l2Hits();
    }

    double totalRequests() {
      return totalHits() + misses();
    }

    double hitRatio() {
      double total = totalRequests();
      return total == 0 ? 1.0 : totalHits() / total;
    }

    double l2DependencyRatio() {
      double totalHit = totalHits();
      return totalHit == 0 ? 0.0 : l2Hits() / totalHit;
    }
  }
}
