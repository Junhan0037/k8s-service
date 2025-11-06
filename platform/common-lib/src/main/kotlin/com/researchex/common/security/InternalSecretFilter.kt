package com.researchex.common.security

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.util.AntPathMatcher
import org.springframework.web.filter.OncePerRequestFilter
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** 내부 호출용 시크릿 검증을 수행하는 필터다. 요청 경로가 보호 대상이면 헤더 값과 저장된 시크릿을 비교한다. */
class InternalSecretFilter(
    private val properties: InternalSecurityProperties
) : OncePerRequestFilter() {

    private val matcher = AntPathMatcher()

    @Throws(ServletException::class, IOException::class)
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (!properties.enabled || !isProtectedPath(request)) {
            filterChain.doFilter(request, response)
            return
        }

        val providedSecret = request.getHeader(properties.headerName)
        if (!isSecretValid(providedSecret, properties.secret)) {
            log.warn("내부 시크릿 검증 실패. path={}, remote={}", request.requestURI, request.remoteAddr)
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED_INTERNAL_CALL")
            return
        }

        filterChain.doFilter(request, response)
    }

    private fun isProtectedPath(request: HttpServletRequest): Boolean {
        val uri = request.requestURI
        return properties.protectedPathPatterns
            .filterNotNull()
            .any { pattern -> matcher.match(pattern, uri) }
    }

    private fun isSecretValid(providedSecret: String?, expectedSecret: String?): Boolean {
        if (providedSecret.isNullOrEmpty() || expectedSecret.isNullOrEmpty()) {
            return false
        }

        val providedHash = sha256(providedSecret)
        val expectedHash = sha256(expectedSecret)

        var result = 0
        for (index in providedHash.indices) {
            result = result or (providedHash[index].toInt() xor expectedHash[index].toInt())
        }
        return result == 0
    }

    private fun sha256(value: String): ByteArray {
        return try {
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
        } catch (ex: Exception) {
            throw IllegalStateException("SHA-256 계산 실패", ex)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(InternalSecretFilter::class.java)
    }
}
