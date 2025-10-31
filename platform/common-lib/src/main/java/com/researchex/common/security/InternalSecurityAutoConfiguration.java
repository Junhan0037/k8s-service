package com.researchex.common.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 내부 시크릿 검증 필터를 자동으로 등록하는 설정.
 */
@AutoConfiguration
@ConditionalOnClass(OncePerRequestFilter.class)
@EnableConfigurationProperties(InternalSecurityProperties.class)
public class InternalSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "researchex.security.internal", name = "enabled", havingValue = "true", matchIfMissing = true)
    public InternalSecretFilter internalSecretFilter(InternalSecurityProperties properties) {
        return new InternalSecretFilter(properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "researchex.security.internal", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<InternalSecretFilter> internalSecretFilterRegistration(InternalSecretFilter filter, InternalSecurityProperties properties) {
        FilterRegistrationBean<InternalSecretFilter> registrationBean = new FilterRegistrationBean<>(filter);
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registrationBean.setName("internalSecretFilter");
        registrationBean.setEnabled(properties.isEnabled());
        return registrationBean;
    }
}
