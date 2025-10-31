package com.researchex.common.tracing;

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
 * TraceId 및 HTTP 로깅 필터 자동 구성.
 */
@AutoConfiguration
@ConditionalOnClass(OncePerRequestFilter.class)
@EnableConfigurationProperties(TracingProperties.class)
public class TracingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "researchex.tracing", name = "enabled", havingValue = "true", matchIfMissing = true)
    public TraceIdFilter traceIdFilter(TracingProperties properties) {
        return new TraceIdFilter(properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "researchex.tracing", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration(TraceIdFilter filter) {
        FilterRegistrationBean<TraceIdFilter> registrationBean = new FilterRegistrationBean<>(filter);
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registrationBean.setName("traceIdFilter");
        return registrationBean;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "researchex.tracing.logging", name = "enabled", havingValue = "true", matchIfMissing = true)
    public HttpLoggingFilter httpLoggingFilter(TracingProperties properties) {
        return new HttpLoggingFilter(properties.getLogging());
    }

    @Bean
    @ConditionalOnProperty(prefix = "researchex.tracing.logging", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<HttpLoggingFilter> httpLoggingFilterRegistration(HttpLoggingFilter filter) {
        FilterRegistrationBean<HttpLoggingFilter> registrationBean = new FilterRegistrationBean<>(filter);
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
        registrationBean.setName("httpLoggingFilter");
        return registrationBean;
    }
}
