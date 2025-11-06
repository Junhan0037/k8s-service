package com.researchex.common.tracing

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/** 트레이싱과 HTTP 로깅 동작을 조절하는 프로퍼티 모음이다. */
@Validated
@ConfigurationProperties(prefix = "researchex.tracing")
class TracingProperties {

    /** TraceId 필터 활성화 여부. */
    var enabled: Boolean = true

    /** 클라이언트와 주고받을 헤더 이름. */
    var headerName: String = "X-Trace-Id"

    /** 비어 있을 때 TraceId를 새로 발급할지 여부. */
    var generateIfMissing: Boolean = true

    /** 요청/응답 로깅 설정. */
    val logging: HttpLogging = HttpLogging()

    /** HTTP 로깅 관련 하위 설정을 담는 클래스다. */
    class HttpLogging {
        /** HTTP 요청/응답 로깅 필터 활성화 여부. */
        var enabled: Boolean = true

        /** 로그에 저장할 최대 페이로드 길이. */
        var maxPayloadLength: Int = 4_096

        /** 요청 본문 로깅 여부. */
        var includeRequestBody: Boolean = true

        /** 응답 본문 로깅 여부. */
        var includeResponseBody: Boolean = false
    }
}
