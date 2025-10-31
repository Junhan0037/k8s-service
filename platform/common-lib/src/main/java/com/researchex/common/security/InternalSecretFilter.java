package com.researchex.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 내부 호출용 시크릿 검증을 수행하는 필터.
 * 요청 경로가 보호 대상이면 헤더 값과 저장된 시크릿을 비교한다.
 */
public class InternalSecretFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InternalSecretFilter.class);

    private final InternalSecurityProperties properties;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public InternalSecretFilter(InternalSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (!properties.isEnabled() || !isProtectedPath(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String providedSecret = request.getHeader(properties.getHeaderName());
        if (!isSecretValid(providedSecret, properties.getSecret())) {
            log.warn("내부 시크릿 검증 실패. path={}, remote={}", request.getRequestURI(), request.getRemoteAddr());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED_INTERNAL_CALL");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isProtectedPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return properties.getProtectedPathPatterns().stream()
                .filter(Objects::nonNull)
                .anyMatch(pattern -> matcher.match(pattern, uri));
    }

    private boolean isSecretValid(String providedSecret, String expectedSecret) {
        if (providedSecret == null || expectedSecret == null) {
            return false;
        }
        // 타이밍 공격을 줄이기 위해 해시 값을 비교한다.
        byte[] providedHash = sha256(providedSecret);
        byte[] expectedHash = sha256(expectedSecret);
        int result = 0;
        for (int i = 0; i < providedHash.length; i++) {
            result |= providedHash[i] ^ expectedHash[i];
        }
        return result == 0;
    }

    private byte[] sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 계산 실패", ex);
        }
    }
}
