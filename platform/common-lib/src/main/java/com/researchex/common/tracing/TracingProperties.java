package com.researchex.common.tracing;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 트레이싱과 HTTP 로깅 동작을 조절하는 프로퍼티 모음. */
@Validated
@ConfigurationProperties(prefix = "researchex.tracing")
// Lombok을 통해 트레이싱 속성에 대한 게터/세터를 일관되게 생성한다.
@Getter
@Setter
public class TracingProperties {

  /** TraceId 필터 활성화 여부. */
  private boolean enabled = true;

  /** 클라이언트와 주고받을 헤더 이름. */
  private String headerName = "X-Trace-Id";

  /** 비어 있을 때 TraceId를 새로 발급할지 여부. */
  private boolean generateIfMissing = true;

  /** 요청/응답 로깅 설정. */
  private final HttpLogging logging = new HttpLogging();

  /** HTTP 로깅 관련 하위 설정을 담는 클래스. */
  @Getter
  @Setter
  public static class HttpLogging {

    /** HTTP 요청/응답 로깅 필터 활성화 여부. */
    private boolean enabled = true;

    /** 로그에 저장할 최대 페이로드 길이. */
    private int maxPayloadLength = 4_096;

    /** 요청 본문 로깅 여부. */
    private boolean includeRequestBody = true;

    /** 응답 본문 로깅 여부. */
    private boolean includeResponseBody = false;
  }
}
