package com.researchex.gateway.trace

import org.springframework.stereotype.Component
import java.util.UUID

/**
 * RFC4122 UUID를 이용해 트레이스 식별자를 생성하는 기본 구현체다.
 * 별도 의존성 없이 재사용 가능한 표준 포맷이기 때문에 로깅 및 추적 도구와의 호환성이 좋다.
 */
@Component
class UuidTraceIdGenerator : TraceIdGenerator {

    override fun generate(): String {
        // 충돌 가능성이 극히 낮은 UUID를 사용해 간단히 트레이스 아이디를 생성한다.
        return UUID.randomUUID().toString()
    }
}
