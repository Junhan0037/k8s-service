package com.researchex.common.security;

import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 내부 서비스 간 통신을 보호하기 위한 시크릿 설정을 보관하는 설정 클래스.
 * 항상 이해하기 쉽게 간단한 주석을 남긴다.
 */
@Validated
@ConfigurationProperties(prefix = "researchex.security.internal")
public class InternalSecurityProperties {

    /**
     * 내부 시크릿 검증 기능 활성화 여부.
     */
    private boolean enabled = true;

    /**
     * 보안 검증에 사용할 헤더 이름. 기본값은 X-Internal-Secret.
     */
    @NotBlank
    private String headerName = "X-Internal-Secret";

    /**
     * 실제 비교에 사용하는 기대 시크릿 값.
     */
    @NotBlank
    private String secret = "change-me";

    /**
     * 보호가 필요한 엔드포인트 패턴 목록. ANT 패턴을 사용한다.
     */
    private List<String> protectedPathPatterns = new ArrayList<>(List.of("/internal/**"));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public List<String> getProtectedPathPatterns() {
        return protectedPathPatterns;
    }

    public void setProtectedPathPatterns(List<String> protectedPathPatterns) {
        this.protectedPathPatterns = protectedPathPatterns;
    }
}
