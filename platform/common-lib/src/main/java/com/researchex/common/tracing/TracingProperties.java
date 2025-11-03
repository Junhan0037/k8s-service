package com.researchex.common.tracing;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 트레이싱과 HTTP 로깅 동작을 조절하는 프로퍼티 모음. */
@Validated
@ConfigurationProperties(prefix = "researchex.tracing")
public class TracingProperties {

  /** TraceId 필터 활성화 여부. */
  private boolean enabled = true;

  /** 클라이언트와 주고받을 헤더 이름. */
  private String headerName = "X-Trace-Id";

  /** 비어 있을 때 TraceId를 새로 발급할지 여부. */
  private boolean generateIfMissing = true;

  /** 요청/응답 로깅 설정. */
  private final HttpLogging logging = new HttpLogging();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getHeaderName() {
    return headerName;
  }

  public void setHeaderName(String headerName) {
    this.headerName = headerName;
  }

  public boolean isGenerateIfMissing() {
    return generateIfMissing;
  }

  public void setGenerateIfMissing(boolean generateIfMissing) {
    this.generateIfMissing = generateIfMissing;
  }

  public HttpLogging getLogging() {
    return logging;
  }

  /** HTTP 로깅 관련 하위 설정을 담는 클래스. */
  public static class HttpLogging {

    /** HTTP 요청/응답 로깅 필터 활성화 여부. */
    private boolean enabled = true;

    /** 로그에 저장할 최대 페이로드 길이. */
    private int maxPayloadLength = 4_096;

    /** 요청 본문 로깅 여부. */
    private boolean includeRequestBody = true;

    /** 응답 본문 로깅 여부. */
    private boolean includeResponseBody = false;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public int getMaxPayloadLength() {
      return maxPayloadLength;
    }

    public void setMaxPayloadLength(int maxPayloadLength) {
      this.maxPayloadLength = maxPayloadLength;
    }

    public boolean isIncludeRequestBody() {
      return includeRequestBody;
    }

    public void setIncludeRequestBody(boolean includeRequestBody) {
      this.includeRequestBody = includeRequestBody;
    }

    public boolean isIncludeResponseBody() {
      return includeResponseBody;
    }

    public void setIncludeResponseBody(boolean includeResponseBody) {
      this.includeResponseBody = includeResponseBody;
    }
  }
}
