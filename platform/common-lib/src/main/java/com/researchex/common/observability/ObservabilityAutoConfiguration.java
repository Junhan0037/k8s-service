package com.researchex.common.observability;

import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.propagation.TraceContext;
import io.micrometer.common.KeyValue;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationFilter;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.annotation.Observed;
import io.micrometer.observation.aop.ObservedAspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.actuate.autoconfigure.observation.ObservationRegistryCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Micrometer/Zipkin 연계를 공통 모듈에서 일괄 설정한다.
 * - 모든 메트릭에 서비스 식별자 태그를 추가해 Grafana 대시보드에서 필터링하기 쉽게 만든다.
 * - @Observed 애노테이션이 AOP 기반으로 동작하도록 ObservedAspect 를 노출한다.
 * - Zipkin Span 에 서비스 이름 태그를 강제로 추가해 서로 다른 서비스가 동일한 지표 공간에서 구분되도록 한다.
 */
@AutoConfiguration
@ConditionalOnClass({MeterRegistry.class, Observed.class})
public class ObservabilityAutoConfiguration {

  private static final Logger log = LoggerFactory.getLogger(ObservabilityAutoConfiguration.class);
  private static final String SERVICE_TAG = "service";

  @Bean
  @ConditionalOnBean(MeterRegistry.class)
  public MeterRegistryCustomizer<MeterRegistry> researchexCommonTags(Environment environment) {
    String serviceName = resolveServiceName(environment);
    return registry -> registry
        .config()
        .commonTags("application", serviceName, SERVICE_TAG, serviceName);
  }

  @Bean
  public ObservationRegistryCustomizer<ObservationRegistry> researchexObservationCustomizer(Environment environment) {
    String serviceName = resolveServiceName(environment);
    return registry -> registry
            .observationConfig()
            .observationFilter(addServiceTagFilter(serviceName));
  }

  @Bean
  @ConditionalOnBean(MeterRegistry.class)
  public ObservedAspect researchexObservedAspect(ObservationRegistry registry) {
    return new ObservedAspect(registry);
  }

  @Bean
  @ConditionalOnClass(SpanHandler.class)
  public SpanHandler researchexServiceTagSpanHandler(Environment environment) {
    String serviceName = resolveServiceName(environment);
    return new SpanHandler() {
      @Override
      public boolean end(TraceContext context, MutableSpan span, Cause cause) {
        span.tag(SERVICE_TAG, serviceName);
        return true;
      }
    };
  }

  private ObservationFilter addServiceTagFilter(String serviceName) {
    return context -> {
      context.addLowCardinalityKeyValue(KeyValue.of(SERVICE_TAG, serviceName));
      return context;
    };
  }

  private String resolveServiceName(Environment environment) {
    String serviceName = environment.getProperty("spring.application.name");
    if (serviceName == null || serviceName.isBlank()) {
      log.warn("spring.application.name 이 비어 있어 service 태그를 'unknown-service'로 설정합니다.");
      return "unknown-service";
    }
    return serviceName;
  }
}
