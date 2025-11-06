package com.researchex.gateway.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.InvalidKeyException
import io.jsonwebtoken.security.Keys
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component
import org.springframework.util.StringUtils
import java.nio.charset.StandardCharsets
import java.security.Key
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * JWT를 파싱하고 서명 검증을 수행하는 컴포넌트다.
 * HMAC 비밀키 또는 RSA 공개키 기반 검증을 모두 지원한다.
 */
@Component
class JwtTokenVerifier(
    private val properties: JwtProperties
) {

    private var verificationKey: Key? = null

    /**
     * 애플리케이션 구동 시점에 서명 검증에 사용할 키를 미리 준비해 둔다.
     */
    @PostConstruct
    fun initialize() {
        verificationKey = resolveVerificationKey()
        if (properties.enabled && verificationKey == null) {
            throw IllegalStateException("JWT 검증이 활성화되어 있지만 사용할 키가 설정되지 않았습니다.")
        }
    }

    /**
     * 전달받은 JWT 토큰에 서명 검증과 클레임 검증을 수행한다.
     *
     * @param token Authorization 헤더에서 추출한 Bearer 토큰 값
     * @return 검증된 Claims 객체
     */
    fun verify(token: String): Claims {
        if (!properties.enabled) {
            throw IllegalStateException("JWT 검증이 비활성화된 상태입니다.")
        }

        val signingKey = verificationKey
            ?: throw IllegalStateException("JWT 검증 키가 초기화되지 않았습니다.")

        return try {
            val parserBuilder = Jwts.parserBuilder()
                .setAllowedClockSkewSeconds(properties.clockSkewSeconds)
                .setSigningKey(signingKey)

            if (StringUtils.hasText(properties.issuer)) {
                parserBuilder.requireIssuer(properties.issuer)
            }
            if (StringUtils.hasText(properties.audience)) {
                parserBuilder.requireAudience(properties.audience)
            }

            // JJWT 파서를 통해 서명 검증과 기본 클레임 검증을 한 번에 수행한다.
            parserBuilder.build().parseClaimsJws(token).body
        } catch (ex: JwtException) {
            throw JwtVerificationException("JWT 서명 또는 클레임 검증에 실패했습니다.", ex)
        } catch (ex: IllegalArgumentException) {
            throw JwtVerificationException("JWT 서명 또는 클레임 검증에 실패했습니다.", ex)
        }
    }

    private fun resolveVerificationKey(): Key? {
        if (StringUtils.hasText(properties.publicKey)) {
            return parsePublicKey(properties.publicKey!!)
        }
        if (StringUtils.hasText(properties.hmacSecret)) {
            return try {
                Keys.hmacShaKeyFor(properties.hmacSecret!!.toByteArray(StandardCharsets.UTF_8))
            } catch (ex: InvalidKeyException) {
                throw IllegalStateException("HMAC 서명 검증에 사용할 비밀키 길이가 충분하지 않습니다.", ex)
            }
        }
        return null
    }

    private fun parsePublicKey(pem: String): Key {
        return try {
            // PEM 헤더/푸터를 제거하고 Base64 디코딩 후 공개키 객체를 생성한다.
            val sanitized = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\\s".toRegex(), "")
            val decoded = Base64.getDecoder().decode(sanitized)
            val keySpec = X509EncodedKeySpec(decoded)
            val keyFactory = KeyFactory.getInstance("RSA")
            val publicKey: PublicKey = keyFactory.generatePublic(keySpec)
            publicKey
        } catch (ex: Exception) {
            throw IllegalStateException("제공된 공개키를 파싱하지 못했습니다. PEM 형식을 확인하세요.", ex)
        }
    }
}
