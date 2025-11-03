package com.researchex.platform.cache;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.AbstractCacheManager;
import org.springframework.lang.Nullable;

/**
 * L1(LRU 기반 Caffeine)과 L2(Redis)를 결합하여 동작하는 CacheManager 구현체. Spring Cache 추상화는
 * 복수 CacheManager를 동시에 지원하지 않으므로, 내부에서 두 캐시 매니저를 위임 받아 다단 캐시를 구성한다.
 */
public class MultiTierCacheManager extends AbstractCacheManager {

  private final CacheManager l1CacheManager;
  @Nullable private final CacheManager l2CacheManager;

  public MultiTierCacheManager(CacheManager l1CacheManager, @Nullable CacheManager l2CacheManager) {
    this.l1CacheManager = l1CacheManager;
    this.l2CacheManager = l2CacheManager;
  }

  @Override
  protected Collection<? extends Cache> loadCaches() {
    Set<String> cacheNames = new LinkedHashSet<>(l1CacheManager.getCacheNames());
    if (l2CacheManager != null) {
      cacheNames.addAll(l2CacheManager.getCacheNames());
    }
    return cacheNames.stream()
        .map(this::getMissingCache)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  @Override
  protected Cache getMissingCache(String name) {
    Cache l1 = l1CacheManager.getCache(name);
    Cache l2 = l2CacheManager != null ? l2CacheManager.getCache(name) : null;
    if (l1 == null && l2 == null) {
      return null;
    }
    return new MultiTierCache(name, l1, l2);
  }
}
