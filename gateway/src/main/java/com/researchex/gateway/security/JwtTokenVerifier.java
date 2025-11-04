package com.researchex.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.InvalidKeyException;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * JWT를 파싱하고 서명 검증을 수행하는 컴포넌트.
 * HMAC 비밀키 또는 RSA 공개키 기반 검증을 모두 지원한다.
 */
@Component
public class JwtTokenVerifier {

    private final JwtProperties properties;
    private Key verificationKey;

    public JwtTokenVerifier(JwtProperties properties) {
        this.properties = properties;
    }

    /**
     * 애플리케이션 구동 시점에 서명 검증에 사용할 키를 미리 준비해 둔다.
     */
    @PostConstruct
    void initialize() {
        this.verificationKey = resolveVerificationKey();
        if (properties.isEnabled() && this.verificationKey == null) {
            throw new IllegalStateException("JWT 검증이 활성화되어 있지만 사용할 키가 설정되지 않았습니다.");
        }
    }

    /**
     * 전달받은 JWT 토큰에 서명 검증과 클레임 검증을 수행한다.
     *
     * @param token Authorization 헤더에서 추출한 Bearer 토큰 값
     * @return 검증된 Claims 객체
     */
    public Claims verify(String token) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("JWT 검증이 비활성화된 상태입니다.");
        }

        try {
            var parserBuilder = Jwts.parserBuilder()
                    .setAllowedClockSkewSeconds(properties.getClockSkewSeconds())
                    .setSigningKey(verificationKey);

            if (StringUtils.hasText(properties.getIssuer())) {
                parserBuilder.requireIssuer(properties.getIssuer());
            }
            if (StringUtils.hasText(properties.getAudience())) {
                parserBuilder.requireAudience(properties.getAudience());
            }

            return parserBuilder.build().parseClaimsJws(token).getBody();
        } catch (JwtException | IllegalArgumentException ex) {
            // 토큰 서명 위조, 만료, 클레임 미일치 등 모든 오류를 단일 예외로 변환한다.
            throw new JwtVerificationException("JWT 서명 또는 클레임 검증에 실패했습니다.", ex);
        }
    }

    private Key resolveVerificationKey() {
        if (StringUtils.hasText(properties.getPublicKey())) {
            return parsePublicKey(properties.getPublicKey());
        }
        if (StringUtils.hasText(properties.getHmacSecret())) {
            try {
                return Keys.hmacShaKeyFor(properties.getHmacSecret().getBytes(StandardCharsets.UTF_8));
            } catch (InvalidKeyException ex) {
                throw new IllegalStateException("HMAC 서명 검증에 사용할 비밀키 길이가 충분하지 않습니다.", ex);
            }
        }
        return null;
    }

    private Key parsePublicKey(String pem) {
        try {
            // PEM 헤더/푸터를 제거하고 Base64 디코딩 후 공개키 객체를 생성한다.
            String sanitized = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] decoded = Base64.getDecoder().decode(sanitized);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey publicKey = keyFactory.generatePublic(keySpec);
            return publicKey;
        } catch (Exception ex) {
            throw new IllegalStateException("제공된 공개키를 파싱하지 못했습니다. PEM 형식을 확인하세요.", ex);
        }
    }
}
