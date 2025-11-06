package com.researchex.common.error

import jakarta.validation.ConstraintViolationException
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.context.request.WebRequest
import org.springframework.web.multipart.support.MissingServletRequestPartException
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

/** 서비스 공통 전역 예외 처리기다. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {

    /** 도메인 비즈니스 예외 처리. */
    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(ex: BusinessException): ResponseEntity<ErrorResponse> {
        val code = ex.errorCode
        val body = ErrorResponse.of(code, currentTraceId(), mapOf("reason" to (ex.message ?: "UNKNOWN")))
        return ResponseEntity.status(code.httpStatus).body(body)
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(ex: ConstraintViolationException): ResponseEntity<Any> {
        val details = mutableMapOf<String, Any?>()
        ex.constraintViolations.forEach { violation ->
            details[violation.propertyPath.toString()] = violation.message
        }
        val body = ErrorResponse.of(CommonErrorCode.VALIDATION_ERROR, currentTraceId(), details)
        return ResponseEntity.status(CommonErrorCode.VALIDATION_ERROR.httpStatus).body(body)
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException): ResponseEntity<Any> {
        val body = ErrorResponse.of(
            CommonErrorCode.FORBIDDEN,
            currentTraceId(),
            mapOf("reason" to (ex.message ?: "Access denied"))
        )
        return ResponseEntity.status(CommonErrorCode.FORBIDDEN.httpStatus).body(body)
    }

    @ExceptionHandler(Exception::class)
    fun handleUncaught(ex: Exception): ResponseEntity<Any> {
        val body = ErrorResponse.of(
            CommonErrorCode.INTERNAL_ERROR,
            currentTraceId(),
            mapOf("reason" to ex.javaClass.simpleName)
        )
        return ResponseEntity.status(CommonErrorCode.INTERNAL_ERROR.httpStatus).body(body)
    }

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest
    ): ResponseEntity<Any> {
        val details = buildFieldErrors(ex.bindingResult.fieldErrors)
        val body = ErrorResponse.of(CommonErrorCode.VALIDATION_ERROR, currentTraceId(), details)
        return ResponseEntity.status(CommonErrorCode.VALIDATION_ERROR.httpStatus).body(body)
    }

    override fun handleHttpMessageNotReadable(
        ex: HttpMessageNotReadableException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest
    ): ResponseEntity<Any> {
        val body = ErrorResponse.of(
            CommonErrorCode.VALIDATION_ERROR,
            currentTraceId(),
            mapOf("reason" to "요청 본문 파싱 실패")
        )
        return ResponseEntity.status(CommonErrorCode.VALIDATION_ERROR.httpStatus).body(body)
    }

    override fun handleMissingServletRequestPart(
        ex: MissingServletRequestPartException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest
    ): ResponseEntity<Any> {
        val body = ErrorResponse.of(
            CommonErrorCode.VALIDATION_ERROR,
            currentTraceId(),
            mapOf("partName" to ex.requestPartName)
        )
        return ResponseEntity.status(CommonErrorCode.VALIDATION_ERROR.httpStatus).body(body)
    }

    private fun buildFieldErrors(fieldErrors: List<FieldError>): Map<String, Any?> {
        val details = mutableMapOf<String, Any?>()
        fieldErrors.forEach { error ->
            details[error.field] = error.defaultMessage
        }
        return details
    }

    private fun currentTraceId(): String {
        return MDC.get(DEFAULT_TRACE_KEY) ?: "UNKNOWN"
    }

    companion object {
        private const val DEFAULT_TRACE_KEY = "traceId"
    }
}
