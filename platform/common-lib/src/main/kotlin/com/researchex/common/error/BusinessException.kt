package com.researchex.common.error

/** 서비스 도메인에서 발생하는 예외의 공통 부모 클래스다. */
open class BusinessException(
    val errorCode: ErrorCode,
    message: String = errorCode.message,
    cause: Throwable? = null
) : RuntimeException(message, cause)
