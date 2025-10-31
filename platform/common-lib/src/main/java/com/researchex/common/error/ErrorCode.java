package com.researchex.common.error;

import org.springframework.http.HttpStatus;

/**
 * 서비스 전역에서 공통으로 사용할 에러 코드 규약.
 */
public interface ErrorCode {

    /**
     * 클라이언트에게 전달할 에러 코드 문자열.
     */
    String getCode();

    /**
     * 사용자 친화적인 메시지.
     */
    String getMessage();

    /**
     * HTTP 응답 상태.
     */
    HttpStatus getHttpStatus();
}
