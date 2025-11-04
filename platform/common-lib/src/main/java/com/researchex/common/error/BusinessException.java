package com.researchex.common.error;

import lombok.Getter;

/** 서비스 도메인에서 발생하는 예외의 공통 부모 클래스. */
@Getter
public class BusinessException extends RuntimeException {

  // 예외에 매핑된 도메인 에러 코드를 노출한다.
  private final ErrorCode errorCode;

  public BusinessException(ErrorCode errorCode) {
    super(errorCode.getMessage());
    this.errorCode = errorCode;
  }

  public BusinessException(ErrorCode errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
    super(message, cause);
    this.errorCode = errorCode;
  }
}
