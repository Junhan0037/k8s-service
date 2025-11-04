package com.researchex.common.gateway;

import com.researchex.common.security.InternalSecurityProperties;
import com.researchex.common.tracing.TracingProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 내부 통신 게이트웨이 관련 공통 빈을 등록하는 자동 구성.
 * TraceId 및 내부 시크릿 헤더를 자동으로 연결해 서비스 간 HTTP 호출 시 보안/추적 정보를 유지한다.
 */
@AutoConfiguration
@EnableConfigurationProperties({TracingProperties.class, InternalSecurityProperties.class})
public class InternalGatewayAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public InternalGatewayHeaderProvider internalGatewayHeaderProvider(TracingProperties tracingProperties, InternalSecurityProperties securityProperties) {
    return new InternalGatewayHeaderProvider(tracingProperties, securityProperties);
  }
}
