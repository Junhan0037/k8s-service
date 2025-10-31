package com.researchex.gateway.security;

/**
 * JWT 서명 및 클레임 검증 실패 시 사용되는 예외다.
 * 런타임 예외로 정의하여 필터 체인에서 제어 흐름을 간결하게 유지한다.
 */
public class JwtVerificationException extends RuntimeException {

    public JwtVerificationException(String message, Throwable cause) {
        super(message, cause);
    }

    public JwtVerificationException(String message) {
        super(message);
    }
}
