package com.researchex.common.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 내부 서비스 간 통신에서 X-Internal-Secret 헤더를 검증하는 필터 자동 구성.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(InternalSecurityProperties.class)
public class InternalSecurityAutoConfiguration {

    /**
     * 내부 시크릿을 검증하는 필터를 등록한다.
     */
    @Bean
    @ConditionalOnProperty(prefix = "researchex.security", name = "internal-secret")
    public FilterRegistrationBean<InternalSecretFilter> internalSecretFilter(InternalSecurityProperties properties) {
        FilterRegistrationBean<InternalSecretFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new InternalSecretFilter(properties));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
