package com.researchex.research.gateway.feign;

import com.researchex.common.gateway.InternalGatewayHeaderProvider;
import com.researchex.common.security.InternalSecurityProperties;
import com.researchex.common.tracing.TracingProperties;
import com.researchex.research.gateway.GatewayClientProperties;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Feign 설정이 내부 헤더 전파를 수행하는지 검증한다. */
class UserGatewayFeignConfigurationTest {

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  void interceptor_shouldAttachTraceAndSecretHeaders() {
    GatewayClientProperties properties = new GatewayClientProperties();
    properties.getUser().setBaseUrl("http://localhost:8200");
    properties.getUser().setConnectTimeout(Duration.ofSeconds(1));
    properties.getUser().setReadTimeout(Duration.ofSeconds(2));

    TracingProperties tracingProperties = new TracingProperties();
    tracingProperties.setHeaderName("X-Trace-Id");
    InternalSecurityProperties securityProperties = new InternalSecurityProperties();
    securityProperties.setSecret("internal-secret");

    InternalGatewayHeaderProvider headerProvider =
        new InternalGatewayHeaderProvider(tracingProperties, securityProperties);
    UserGatewayFeignConfiguration configuration =
        new UserGatewayFeignConfiguration(properties);

    RequestInterceptor interceptor = configuration.userGatewayHeaderInterceptor(headerProvider);

    MDC.put("traceId", "trace-feign-1");
    RequestTemplate template = new RequestTemplate();
    interceptor.apply(template);

    assertThat(template.headers().get("X-Trace-Id")).containsExactly("trace-feign-1");
    assertThat(template.headers().get("X-Internal-Secret")).containsExactly("internal-secret");
  }
}
