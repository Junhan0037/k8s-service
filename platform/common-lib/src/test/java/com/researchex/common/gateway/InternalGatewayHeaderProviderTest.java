package com.researchex.common.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.researchex.common.security.InternalSecurityProperties;
import com.researchex.common.tracing.TracingProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;

/** InternalGatewayHeaderProvider 동작을 검증하는 단위 테스트. */
class InternalGatewayHeaderProviderTest {

  private final TracingProperties tracingProperties = new TracingProperties();
  private final InternalSecurityProperties securityProperties = new InternalSecurityProperties();

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  void enrich_shouldAppendTraceIdAndSecretWhenAvailable() {
    tracingProperties.setHeaderName("X-Trace-Id");
    securityProperties.setSecret("internal-secret");
    InternalGatewayHeaderProvider provider =
        new InternalGatewayHeaderProvider(tracingProperties, securityProperties);

    MDC.put("traceId", "trace-123");
    HttpHeaders headers = new HttpHeaders();

    provider.enrich(headers);

    assertThat(headers.getFirst("X-Trace-Id")).isEqualTo("trace-123");
    assertThat(headers.getFirst("X-Internal-Secret")).isEqualTo("internal-secret");
  }

  @Test
  void enrich_shouldNotOverrideExistingHeaders() {
    tracingProperties.setHeaderName("X-Trace-Id");
    securityProperties.setSecret("internal-secret");
    InternalGatewayHeaderProvider provider =
        new InternalGatewayHeaderProvider(tracingProperties, securityProperties);

    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Trace-Id", "existing-trace");
    headers.add("X-Internal-Secret", "predefined");
    MDC.put("traceId", "trace-should-not-override");

    provider.enrich(headers);

    assertThat(headers.getFirst("X-Trace-Id")).isEqualTo("existing-trace");
    assertThat(headers.getFirst("X-Internal-Secret")).isEqualTo("predefined");
  }
}
