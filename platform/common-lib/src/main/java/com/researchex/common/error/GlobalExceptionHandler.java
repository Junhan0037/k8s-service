package com.researchex.common.error;

import jakarta.validation.ConstraintViolationException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** 서비스 공통 전역 예외 처리기. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final String DEFAULT_TRACE_KEY = "traceId";

  /** 도메인 비즈니스 예외 처리. */
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex) {
    ErrorCode code = ex.getErrorCode();
    ErrorResponse body =
        ErrorResponse.of(code, currentTraceId(), Map.of("reason", ex.getMessage()));
    return ResponseEntity.status(code.getHttpStatus()).body(body);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex) {
    Map<String, Object> details = new HashMap<>();
    ex.getConstraintViolations()
        .forEach(
            violation ->
                details.put(violation.getPropertyPath().toString(), violation.getMessage()));

    ErrorResponse body =
        ErrorResponse.of(CommonErrorCode.VALIDATION_ERROR, currentTraceId(), details);
    return ResponseEntity.status(CommonErrorCode.VALIDATION_ERROR.getHttpStatus()).body(body);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex) {
    ErrorResponse body =
        ErrorResponse.of(
            CommonErrorCode.FORBIDDEN, currentTraceId(), Map.of("reason", ex.getMessage()));
    return ResponseEntity.status(CommonErrorCode.FORBIDDEN.getHttpStatus()).body(body);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Object> handleUncaught(Exception ex) {
    ErrorResponse body =
        ErrorResponse.of(
            CommonErrorCode.INTERNAL_ERROR,
            currentTraceId(),
            Map.of("reason", ex.getClass().getSimpleName()));
    return ResponseEntity.status(CommonErrorCode.INTERNAL_ERROR.getHttpStatus()).body(body);
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    Map<String, Object> details = buildFieldErrors(ex.getBindingResult().getFieldErrors());
    ErrorResponse body =
        ErrorResponse.of(CommonErrorCode.VALIDATION_ERROR, currentTraceId(), details);
    return ResponseEntity.status(CommonErrorCode.VALIDATION_ERROR.getHttpStatus()).body(body);
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ErrorResponse body =
        ErrorResponse.of(
            CommonErrorCode.VALIDATION_ERROR, currentTraceId(), Map.of("reason", "요청 본문 파싱 실패"));
    return ResponseEntity.status(CommonErrorCode.VALIDATION_ERROR.getHttpStatus()).body(body);
  }

  @Override
  protected ResponseEntity<Object> handleMissingServletRequestPart(
      MissingServletRequestPartException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ErrorResponse body =
        ErrorResponse.of(
            CommonErrorCode.VALIDATION_ERROR,
            currentTraceId(),
            Map.of("partName", ex.getRequestPartName()));
    return ResponseEntity.status(CommonErrorCode.VALIDATION_ERROR.getHttpStatus()).body(body);
  }

  private Map<String, Object> buildFieldErrors(List<FieldError> fieldErrors) {
    Map<String, Object> details = new HashMap<>();
    fieldErrors.forEach(error -> details.put(error.getField(), error.getDefaultMessage()));
    return details;
  }

  private String currentTraceId() {
    // MDC는 null을 반환할 수 있으므로 기본값을 수동으로 지정한다.
    String traceId = MDC.get(DEFAULT_TRACE_KEY);
    return traceId != null ? traceId : "UNKNOWN";
  }
}
