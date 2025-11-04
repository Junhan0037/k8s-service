package com.researchex.common.error;

import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

/** REST API 전역 예외 처리를 자동 구성한다. */
@Configuration
@ConditionalOnClass(RestControllerAdvice.class)
public class ErrorHandlingAutoConfiguration {

  /** 공통 예외 응답을 담당하는 글로벌 어드바이스. */
  @RestControllerAdvice
  static class GlobalRestExceptionHandler {

    private static final String TRACE_ID_KEY = "traceId";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
        MethodArgumentNotValidException ex) {
      String defaultMessage =
          ex.getBindingResult().getFieldErrors().stream()
              .map(this::formatFieldError)
              .findFirst()
              .orElseGet(ex::getMessage);
      ErrorResponse body = new ErrorResponse("VALIDATION_ERROR", defaultMessage, currentTraceId());
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
      ErrorResponse body = new ErrorResponse("INTERNAL_ERROR", ex.getMessage(), currentTraceId());
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private String currentTraceId() {
      String traceId = MDC.get(TRACE_ID_KEY);
      return traceId != null ? traceId : UUID.randomUUID().toString();
    }

    private String formatFieldError(FieldError fieldError) {
      return "%s: %s".formatted(fieldError.getField(), fieldError.getDefaultMessage());
    }
  }

  /**
   * 공통 에러 응답 모델.
   *
   * @param code 오류 유형
   * @param message 오류 메시지
   * @param traceId 추적 식별자
   */
  public record ErrorResponse(String code, String message, String traceId) {}
}
