package com.researchex.gateway.security

/**
 * JWT 서명 및 클레임 검증 실패 시 사용되는 예외다.
 * 런타임 예외로 정의하여 필터 체인에서 제어 흐름을 간결하게 유지한다.
 */
class JwtVerificationException : RuntimeException {

    /**
     * 검증 실패 사유와 원인 예외를 함께 전달한다.
     */
    constructor(message: String, cause: Throwable) : super(message, cause)

    /**
     * 검증 실패 사유만 전달할 때 이용한다.
     */
    constructor(message: String) : super(message)
}
