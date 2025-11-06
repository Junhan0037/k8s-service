package com.researchex.common.error

import org.slf4j.MDC
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.util.UUID

/** REST API 전역 예외 처리를 자동 구성한다. */
@Configuration
@ConditionalOnClass(RestControllerAdvice::class)
class ErrorHandlingAutoConfiguration {

    /** 공통 예외 응답을 담당하는 글로벌 어드바이스다. */
    @RestControllerAdvice
    class GlobalRestExceptionHandler {

        @ExceptionHandler(MethodArgumentNotValidException::class)
        fun handleValidationException(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
            val defaultMessage = ex.bindingResult.fieldErrors
                .map { formatFieldError(it) }
                .firstOrNull()
                ?: (ex.message ?: "입력값이 올바르지 않습니다.")

            val body = ErrorResponse("VALIDATION_ERROR", defaultMessage, currentTraceId())
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body)
        }

        @ExceptionHandler(Exception::class)
        fun handleGenericException(ex: Exception): ResponseEntity<ErrorResponse> {
            val body = ErrorResponse("INTERNAL_ERROR", ex.message ?: "알 수 없는 오류가 발생했습니다.", currentTraceId())
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body)
        }

        private fun currentTraceId(): String {
            val traceId = MDC.get(TRACE_ID_KEY)
            return traceId ?: UUID.randomUUID().toString()
        }

        private fun formatFieldError(fieldError: FieldError): String {
            return "${fieldError.field}: ${fieldError.defaultMessage}"
        }

        companion object {
            private const val TRACE_ID_KEY = "traceId"
        }
    }

    /**
     * 공통 에러 응답 모델.
     *
     * @param code 오류 유형
     * @param message 오류 메시지
     * @param traceId 추적 식별자
     */
    data class ErrorResponse(val code: String, val message: String, val traceId: String)
}
