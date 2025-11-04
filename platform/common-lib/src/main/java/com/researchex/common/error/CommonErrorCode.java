package com.researchex.common.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** 가장 자주 등장하는 공통 에러 코드를 미리 정의한다. */
@Getter
public enum CommonErrorCode implements ErrorCode {
  VALIDATION_ERROR("COMMON-001", HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
  UNAUTHORIZED("COMMON-002", HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
  FORBIDDEN("COMMON-003", HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
  NOT_FOUND("COMMON-004", HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
  INTERNAL_ERROR("COMMON-999", HttpStatus.INTERNAL_SERVER_ERROR, "예상하지 못한 오류가 발생했습니다.");

  // HTTP 상태 코드와 메시지를 함께 반환해 호출 측이 에러 응답을 쉽게 구성한다.
  private final String code;
  private final HttpStatus httpStatus;
  private final String message;

  CommonErrorCode(String code, HttpStatus httpStatus, String message) {
    this.code = code;
    this.httpStatus = httpStatus;
    this.message = message;
  }
}
