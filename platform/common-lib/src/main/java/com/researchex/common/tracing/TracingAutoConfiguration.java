package com.researchex.common.tracing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/** HTTP 요청당 TraceId를 생성하고 응답 헤더로 반환하는 필터 자동 구성. */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class TracingAutoConfiguration {

  private static final String TRACE_ID_KEY = "traceId";
  private static final String TRACE_HEADER = "X-Trace-Id";

  @Bean
  public FilterRegistrationBean<TraceIdFilter> traceIdFilter() {
    FilterRegistrationBean<TraceIdFilter> registrationBean = new FilterRegistrationBean<>();
    registrationBean.setFilter(new TraceIdFilter());
    registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);
    registrationBean.addUrlPatterns("/*");
    return registrationBean;
  }

  /** TraceId를 관리하는 OncePerRequestFilter 구현. */
  static class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
      String traceId = resolveTraceId(request);
      MDC.put(TRACE_ID_KEY, traceId);
      response.setHeader(TRACE_HEADER, traceId);

      try {
        filterChain.doFilter(request, response);
      } finally {
        MDC.remove(TRACE_ID_KEY);
      }
    }

    private String resolveTraceId(HttpServletRequest request) {
      String headerValue = request.getHeader(TRACE_HEADER);
      if (StringUtils.hasText(headerValue)) {
        return headerValue;
      }
      return UUID.randomUUID().toString();
    }
  }
}
