package com.researchex.common.security;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * 내부 API 보호를 위해 사용하는 시크릿 헤더 설정을 정의한다.
 * 기본적으로 비활성화되어 있으며 시크릿 값이 주입된 경우에만 필터가 동작한다.
 */
@ConfigurationProperties(prefix = "researchex.security")
public class InternalSecurityProperties {

    /**
     * 내부 서비스 간 통신 시 검증할 시크릿 값.
     */
    private String secret;

    /**
     * 시크릿이 위치한 요청 헤더 이름.
     */
    private String headerName = "X-Internal-Secret";

    /**
     * 보호 대상 경로 패턴 리스트. 기본값은 /internal/** 형태의 내부 API 이다.
     */
    private List<String> protectedPathPatterns = new ArrayList<>(List.of("/internal/**"));

    public boolean isEnabled() {
        return StringUtils.hasText(secret);
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public List<String> getProtectedPathPatterns() {
        return protectedPathPatterns;
    }

    public void setProtectedPathPatterns(List<String> protectedPathPatterns) {
        this.protectedPathPatterns = protectedPathPatterns;
    }
}
