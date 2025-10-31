package com.researchex.gateway.security;

import com.researchex.gateway.trace.TraceIdFilter;
import io.jsonwebtoken.Claims;
import java.nio.charset.StandardCharsets;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * JWT 검증을 수행하는 전역 필터.
 * 화이트리스트에 포함되지 않은 모든 경로에 대해 Authorization 헤더의 Bearer 토큰을 검증한다.
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String CLAIMS_ATTRIBUTE = "researchex.jwtClaims";
    private static final String SUBJECT_HEADER = "X-Auth-Subject";

    private final JwtProperties properties;
    private final JwtTokenVerifier tokenVerifier;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtAuthenticationFilter(JwtProperties properties, JwtTokenVerifier tokenVerifier) {
        this.properties = properties;
        this.tokenVerifier = tokenVerifier;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }

        String requestPath = exchange.getRequest().getURI().getPath();
        if (isWhitelisted(requestPath)) {
            return chain.filter(exchange);
        }

        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange, "Authorization 헤더가 존재하지 않거나 Bearer 토큰 형식이 아닙니다.");
        }

        String rawToken = authorization.substring(BEARER_PREFIX.length());
        try {
            Claims claims = tokenVerifier.verify(rawToken);
            // 필요 시 다운스트림 서비스에서 사용자 식별에 활용할 수 있도록 Subject를 헤더로 전달한다.
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .headers(headers -> headers.set(SUBJECT_HEADER, claims.getSubject()))
                    .build();
            exchange.getAttributes().put(CLAIMS_ATTRIBUTE, claims);
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        } catch (JwtVerificationException ex) {
            return unauthorized(exchange, ex.getMessage());
        }
    }

    @Override
    public int getOrder() {
        // TraceId 필터 다음에 동작해야 하므로 우선순위를 약간 낮게 설정한다.
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    private boolean isWhitelisted(String path) {
        return properties.getWhitelistPatterns().stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String traceId = (String) exchange.getAttributeOrDefault(TraceIdFilter.TRACE_ID_ATTRIBUTE, "");
        String safeMessage = escapeJson(message);
        String safeTraceId = escapeJson(traceId);
        byte[] body = ("{\"code\":\"UNAUTHORIZED\",\"message\":\"" + safeMessage
                + "\",\"traceId\":\"" + safeTraceId + "\"}").getBytes(StandardCharsets.UTF_8);
        var buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\"", "\\\"");
    }
}
