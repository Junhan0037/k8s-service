package com.researchex.gateway.security;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JWT 검증에 필요한 설정 값을 보관하는 구성 프로퍼티 객체다.
 * 서명 키 유형(HMAC 또는 RSA), 허용 오차, 화이트리스트 경로 등을 외부 설정으로 쉽게 바꿀 수 있다.
 */
@Validated
@ConfigurationProperties(prefix = "gateway.security.jwt")
// Lombok을 활용해 게터/세터를 자동 생성하여 속성 변경 시 유지보수를 단순화한다.
@Getter
@Setter
public class JwtProperties {

    /**
     * 게이트웨이에서 JWT 검증을 수행할지 여부.
     * 개발 환경에서 임시로 비활성화해야 하는 경우 true/false로 토글할 수 있다.
     */
    private boolean enabled = true;

    /**
     * HMAC 서명 검증에 사용할 비밀 키. RSA 공개키가 제공되면 이 값은 무시된다.
     */
    private String hmacSecret;

    /**
     * RSA 혹은 ECDSA 공개키 값(PEM 형식). 값이 존재하면 공개키 기반 검증을 우선한다.
     */
    private String publicKey;

    /**
     * 토큰에서 기대하는 issuer 값. 미설정 시 issuer 검증을 생략한다.
     */
    private String issuer;

    /**
     * 토큰에서 기대하는 audience 값. 미설정 시 audience 검증을 생략한다.
     */
    private String audience;

    /**
     * 서버 간 시계 오차 허용 범위(초). JJWT 파서의 allowed clock skew 설정에 사용된다.
     */
    @Min(0)
    private long clockSkewSeconds = 30;

    /**
     * JWT 검증을 건너뛰어야 하는 경로 패턴 목록(Ant 스타일).
     */
    @NotEmpty
    private List<String> whitelistPatterns = new ArrayList<>(List.of("/actuator/**"));

}
