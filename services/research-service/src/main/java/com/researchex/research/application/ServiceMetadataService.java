package com.researchex.research.application;

import com.researchex.platform.cache.CacheNames;
import com.researchex.research.api.ServiceMetadataResponse;
import io.micrometer.observation.annotation.Observed;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

/**
 * 서비스 메타데이터 응답을 관리하는 전용 서비스.
 *
 * 내용이 자주 변하지 않는 정적 정보이므로 1시간 캐시 정책을 적용해 게이트웨이/운영 도구 호출 부하를
 * 줄인다.</p>
 */
@Service
public class ServiceMetadataService {

  private final String serviceName;
  private final String serviceDescription;

  public ServiceMetadataService(
      @Value("${spring.application.name}") String serviceName,
      @Value("${service.description}") String serviceDescription) {
    this.serviceName = serviceName;
    this.serviceDescription = serviceDescription;
  }

  /** 캐시가 만료될 때만 새 타임스탬프를 생성해 반환한다. */
  @Cacheable(cacheNames = CacheNames.STATIC_REFERENCE, key = "'service-metadata'")
  @Observed(
      name = "researchex.research.metadata.cache-miss",
      contextualName = "research-metadata-cache-miss",
      lowCardinalityKeyValues = {"cache", CacheNames.STATIC_REFERENCE})
  public ServiceMetadataResponse fetchMetadata() {
    return new ServiceMetadataResponse(serviceName, serviceDescription, OffsetDateTime.now());
  }
}
