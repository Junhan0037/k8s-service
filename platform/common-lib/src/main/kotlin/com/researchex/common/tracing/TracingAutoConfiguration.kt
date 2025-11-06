package com.researchex.common.tracing

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered

/** HTTP 요청당 TraceId를 생성하고 응답 헤더로 반환하는 필터 자동 구성이다. */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(TracingProperties::class)
class TracingAutoConfiguration {

    @Bean
    fun traceIdFilter(properties: TracingProperties): FilterRegistrationBean<TraceIdFilter> {
        return FilterRegistrationBean<TraceIdFilter>().apply {
            filter = TraceIdFilter(properties)
            order = Ordered.HIGHEST_PRECEDENCE
            addUrlPatterns("/*")
        }
    }
}
