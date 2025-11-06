package com.researchex.common.observability

import brave.handler.MutableSpan
import brave.handler.SpanHandler
import brave.propagation.TraceContext
import io.micrometer.common.KeyValue
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.observation.ObservationFilter
import io.micrometer.observation.ObservationRegistry
import io.micrometer.observation.annotation.Observed
import io.micrometer.observation.aop.ObservedAspect
import org.slf4j.LoggerFactory
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer
import org.springframework.boot.actuate.autoconfigure.observation.ObservationRegistryCustomizer
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Bean
import org.springframework.core.env.Environment

/**
 * Micrometer/Zipkin 연계를 공통 모듈에서 일괄 설정한다.
 * - 모든 메트릭에 서비스 식별자 태그를 추가해 Grafana 대시보드에서 필터링하기 쉽게 만든다.
 * - @Observed 애노테이션이 AOP 기반으로 동작하도록 ObservedAspect 를 노출한다.
 * - Zipkin Span 에 서비스 이름 태그를 강제로 추가해 서로 다른 서비스가 동일한 지표 공간에서 구분되도록 한다.
 */
@AutoConfiguration
@ConditionalOnClass(MeterRegistry::class, Observed::class)
class ObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnBean(MeterRegistry::class)
    fun researchexCommonTags(environment: Environment): MeterRegistryCustomizer<MeterRegistry> {
        val serviceName = resolveServiceName(environment)
        return MeterRegistryCustomizer { registry ->
            registry.config().commonTags("application", serviceName, SERVICE_TAG, serviceName)
        }
    }

    @Bean
    fun researchexObservationCustomizer(environment: Environment): ObservationRegistryCustomizer<ObservationRegistry> {
        val serviceName = resolveServiceName(environment)
        return ObservationRegistryCustomizer { registry ->
            registry.observationConfig().observationFilter(addServiceTagFilter(serviceName))
        }
    }

    @Bean
    @ConditionalOnBean(MeterRegistry::class)
    fun researchexObservedAspect(registry: ObservationRegistry): ObservedAspect {
        return ObservedAspect(registry)
    }

    @Bean
    @ConditionalOnClass(SpanHandler::class)
    fun researchexServiceTagSpanHandler(environment: Environment): SpanHandler {
        val serviceName = resolveServiceName(environment)
        return object : SpanHandler() {
            override fun end(context: TraceContext, span: MutableSpan, cause: Cause): Boolean {
                span.tag(SERVICE_TAG, serviceName)
                return true
            }
        }
    }

    private fun addServiceTagFilter(serviceName: String): ObservationFilter {
        return ObservationFilter { context ->
            context.addLowCardinalityKeyValue(KeyValue.of(SERVICE_TAG, serviceName))
            context
        }
    }

    private fun resolveServiceName(environment: Environment): String {
        val serviceName = environment.getProperty("spring.application.name")
        if (serviceName.isNullOrBlank()) {
            log.warn("spring.application.name 이 비어 있어 service 태그를 'unknown-service'로 설정합니다.")
            return "unknown-service"
        }
        return serviceName
    }

    companion object {
        private val log = LoggerFactory.getLogger(ObservabilityAutoConfiguration::class.java)
        private const val SERVICE_TAG = "service"
    }
}
