package com.researchex.common.tracing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/** TraceId를 생성하고 MDC에 주입하는 필터. */
public class TraceIdFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);
  private static final String DEFAULT_MDC_KEY = "traceId";

  private final TracingProperties properties;

  public TraceIdFilter(TracingProperties properties) {
    this.properties = properties;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String headerName = properties.getHeaderName();
    String incomingTraceId = request.getHeader(headerName);
    String traceId = resolveTraceId(incomingTraceId);

    MDC.put(DEFAULT_MDC_KEY, traceId);
    response.setHeader(headerName, traceId);

    try {
      filterChain.doFilter(request, response);
    } finally {
      // 필터 체인을 빠져나갈 때 MDC를 반드시 정리한다.
      MDC.remove(DEFAULT_MDC_KEY);
    }
  }

  private String resolveTraceId(String incomingTraceId) {
    if (StringUtils.hasText(incomingTraceId)) {
      return incomingTraceId;
    }

    if (properties.isGenerateIfMissing()) {
      String generated = UUID.randomUUID().toString();
      log.debug("TraceId가 없어 새로 발급했습니다. traceId={}", generated);
      return generated;
    }

    return Optional.ofNullable(MDC.get(DEFAULT_MDC_KEY)).orElse("UNKNOWN");
  }
}
