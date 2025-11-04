package com.researchex.common.gateway;

import com.researchex.common.security.InternalSecurityProperties;
import com.researchex.common.tracing.TracingProperties;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * 내부 서비스 간 통신 시 필수 헤더(X-Trace-Id, X-Internal-Secret 등)를 주입하는 헬퍼 컴포넌트.
 * 필터에서 생성된 TraceId와 내부 시크릿 값을 중앙에서 일관되게 관리해 각 게이트웨이 구현이 중복 로직을 갖지 않도록 한다.
 */
public class InternalGatewayHeaderProvider {

  private static final String DEFAULT_TRACE_ID_MDC_KEY = "traceId";

  private final TracingProperties tracingProperties;
  private final InternalSecurityProperties securityProperties;

  public InternalGatewayHeaderProvider(TracingProperties tracingProperties, InternalSecurityProperties securityProperties) {
    this.tracingProperties = tracingProperties;
    this.securityProperties = securityProperties;
  }

  /**
   * Headers 객체에 내부 통신용 공통 헤더를 추가한다.
   */
  public void enrich(HttpHeaders headers) {
    String traceHeaderName = Optional.ofNullable(tracingProperties).map(TracingProperties::getHeaderName).orElse("X-Trace-Id");
    String traceId = MDC.get(DEFAULT_TRACE_ID_MDC_KEY);
    if (StringUtils.hasText(traceId) && !headers.containsKey(traceHeaderName)) {
      headers.set(traceHeaderName, traceId);
    }

    if (securityProperties != null && securityProperties.isEnabled()) {
      String headerName = securityProperties.getHeaderName();
      String secretValue = securityProperties.getSecret();
      if (!headers.containsKey(headerName) && StringUtils.hasText(secretValue)) {
        headers.set(headerName, secretValue);
      }
    }
  }
}
