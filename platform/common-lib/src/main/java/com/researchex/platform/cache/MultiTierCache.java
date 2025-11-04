package com.researchex.platform.cache;

import java.util.Objects;
import java.util.concurrent.Callable;
import org.springframework.cache.Cache;
import org.springframework.cache.support.SimpleValueWrapper;
import org.springframework.lang.Nullable;

/**
 * Caffeine(L1)과 Redis(L2)를 묶어 일관된 캐시 동작을 제공하는 Cache 구현체이다. L1 조회 실패 시
 * L2에서 데이터를 가져와 L1에 재적재하고, 쓰기 연산은 두 계층에 모두 반영한다. 단일 책임을 유지하기 위해
 * 동기화 전략은 Spring Cache 추상화에 위임한다.
 */
public class MultiTierCache implements Cache {

  private final String name;
  @Nullable private final Cache l1Cache;
  @Nullable private final Cache l2Cache;
  private final CacheMetricsRecorder metricsRecorder;

  public MultiTierCache(
      String name, @Nullable Cache l1Cache, @Nullable Cache l2Cache, CacheMetricsRecorder metricsRecorder) {
    this.name = name;
    this.l1Cache = l1Cache;
    this.l2Cache = l2Cache;
    this.metricsRecorder = metricsRecorder;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public Object getNativeCache() {
    return new Object[] {l1Cache != null ? l1Cache.getNativeCache() : null, l2Cache != null ? l2Cache.getNativeCache() : null};
  }

  @Override
  @Nullable
  public ValueWrapper get(Object key) {
    ValueWrapper valueFromL1 = getFromCache(l1Cache, key);
    if (valueFromL1 != null) {
      metricsRecorder.recordL1Hit(name);
      return valueFromL1;
    }

    ValueWrapper valueFromL2 = getFromCache(l2Cache, key);
    if (valueFromL2 != null && l1Cache != null) {
      metricsRecorder.recordL2Hit(name);
      l1Cache.put(key, Objects.requireNonNull(valueFromL2).get());
    }
    if (valueFromL2 == null) {
      metricsRecorder.recordMiss(name);
    }
    return valueFromL2;
  }

  @Override
  @Nullable
  public <T> T get(Object key, @Nullable Class<T> type) {
    ValueWrapper value = get(key);
    if (value == null) {
      return null;
    }
    Object targetValue = value.get();
    if (type != null && !type.isInstance(targetValue)) {
      throw new IllegalStateException("Cached value is not of required type: " + type.getName() + ", actual: " + targetValue.getClass().getName());
    }
    @SuppressWarnings("unchecked")
    T castedValue = (T) targetValue;
    return castedValue;
  }

  @Override
  public <T> T get(Object key, Callable<T> valueLoader) {
    ValueWrapper wrapper = get(key);
    if (wrapper != null) {
      @SuppressWarnings("unchecked")
      T value = (T) wrapper.get();
      return value;
    }

    try {
      T value = valueLoader.call();
      put(key, value);
      return value;
    } catch (Exception ex) {
      throw new ValueRetrievalException(key, valueLoader, ex);
    }
  }

  @Override
  public void put(Object key, @Nullable Object value) {
    if (l1Cache != null) {
      l1Cache.put(key, value);
    }
    if (l2Cache != null) {
      l2Cache.put(key, value);
    }
  }

  @Override
  @Nullable
  public ValueWrapper putIfAbsent(Object key, @Nullable Object value) {
    ValueWrapper currentL1 = l1Cache != null ? l1Cache.putIfAbsent(key, value) : null;
    ValueWrapper currentL2 = l2Cache != null ? l2Cache.putIfAbsent(key, value) : null;

    if (currentL1 != null) {
      return currentL1;
    }
    if (currentL2 != null) {
      // L1 기준으로 putIfAbsent를 수행했는데 L2에서 이미 존재하는 경우 L1도 동일 데이터로 맞춘다.
      if (l1Cache != null) {
        l1Cache.put(key, Objects.requireNonNull(currentL2).get());
      }
      return currentL2;
    }

    return value == null ? null : new SimpleValueWrapper(value);
  }

  @Override
  public void evict(Object key) {
    if (l1Cache != null) {
      l1Cache.evict(key);
    }
    if (l2Cache != null) {
      l2Cache.evict(key);
    }
  }

  @Override
  public void clear() {
    if (l1Cache != null) {
      l1Cache.clear();
    }
    if (l2Cache != null) {
      l2Cache.clear();
    }
  }

  @Nullable
  private ValueWrapper getFromCache(@Nullable Cache cache, Object key) {
    if (cache == null) {
      return null;
    }
    return cache.get(key);
  }
}
