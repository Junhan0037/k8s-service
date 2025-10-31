package com.researchex.gateway.trace;

/**
 * 트레이스 식별자를 생성하는 전략을 추상화한 인터페이스다.
 * 구현체를 주입해 두면 테스트 시 고정된 값을 주입하거나 다른 포맷으로 확장하기가 수월하다.
 */
public interface TraceIdGenerator {

    /**
     * 새로운 트레이스 식별자를 생성한다.
     *
     * @return 생성된 트레이스 식별자 문자열
     */
    String generate();
}
