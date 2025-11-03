package com.researchex.common.tracing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

/** 요청/응답을 마스킹 후 로깅하는 필터. */
public class HttpLoggingFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(HttpLoggingFilter.class);

  private final TracingProperties.HttpLogging loggingProperties;

  public HttpLoggingFilter(TracingProperties.HttpLogging loggingProperties) {
    this.loggingProperties = loggingProperties;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    if (!loggingProperties.isEnabled()) {
      filterChain.doFilter(request, response);
      return;
    }

    ContentCachingRequestWrapper requestWrapper =
        new ContentCachingRequestWrapper(request, loggingProperties.getMaxPayloadLength());
    ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

    long startAt = System.currentTimeMillis();
    try {
      filterChain.doFilter(requestWrapper, responseWrapper);
    } finally {
      long duration = System.currentTimeMillis() - startAt;
      logExchange(requestWrapper, responseWrapper, duration);
      responseWrapper.copyBodyToResponse();
    }
  }

  private void logExchange(
      ContentCachingRequestWrapper request, ContentCachingResponseWrapper response, long duration) {
    try {
      String traceId = MDC.get("traceId");
      String method = request.getMethod();
      String uri = request.getRequestURI();
      int status = response.getStatus();

      String requestBody =
          loggingProperties.isIncludeRequestBody()
              ? extractBody(request.getContentAsByteArray())
              : "";
      String responseBody =
          loggingProperties.isIncludeResponseBody()
              ? extractBody(response.getContentAsByteArray())
              : "";

      log.info(
          "[HTTP] traceId={} {} {} status={} durationMs={} reqBody={} respBody={}",
          traceId,
          method,
          uri,
          status,
          duration,
          requestBody,
          responseBody);
    } catch (Exception ex) {
      log.warn("HTTP 로깅 중 오류가 발생했습니다.", ex);
    }
  }

  private String extractBody(byte[] content) {
    if (content == null || content.length == 0) {
      return "";
    }
    String raw = new String(content, StandardCharsets.UTF_8);
    return SensitiveDataMasker.mask(truncate(raw));
  }

  private String truncate(String raw) {
    if (raw.length() <= loggingProperties.getMaxPayloadLength()) {
      return raw;
    }
    return raw.substring(0, loggingProperties.getMaxPayloadLength()) + "...(truncated)";
  }
}
