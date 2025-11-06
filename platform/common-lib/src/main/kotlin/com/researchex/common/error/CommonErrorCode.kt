package com.researchex.common.error

import org.springframework.http.HttpStatus

/** 가장 자주 등장하는 공통 에러 코드를 미리 정의한다. */
enum class CommonErrorCode(
    override val code: String,
    override val httpStatus: HttpStatus,
    override val message: String
) : ErrorCode {
    VALIDATION_ERROR("COMMON-001", HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    UNAUTHORIZED("COMMON-002", HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN("COMMON-003", HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    NOT_FOUND("COMMON-004", HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_ERROR("COMMON-999", HttpStatus.INTERNAL_SERVER_ERROR, "예상하지 못한 오류가 발생했습니다.");
}
