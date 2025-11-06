package com.researchex.common.resilience

import com.researchex.common.resilience.ResilienceProperties.CircuitBreakerOverrides
import com.researchex.common.resilience.ResilienceProperties.CircuitBreakerProperties
import com.researchex.common.resilience.ResilienceProperties.ExecutorProperties
import com.researchex.common.resilience.ResilienceProperties.RateLimiterOverrides
import com.researchex.common.resilience.ResilienceProperties.RateLimiterProperties
import com.researchex.common.resilience.ResilienceProperties.RetryOverrides
import com.researchex.common.resilience.ResilienceProperties.RetryProperties
import com.researchex.common.resilience.ResilienceProperties.SemaphoreBulkheadOverrides
import com.researchex.common.resilience.ResilienceProperties.SemaphoreBulkheadProperties
import com.researchex.common.resilience.ResilienceProperties.ServiceProperties
import com.researchex.common.resilience.ResilienceProperties.ThreadPoolBulkheadOverrides
import com.researchex.common.resilience.ResilienceProperties.ThreadPoolBulkheadProperties
import io.github.resilience4j.bulkhead.BulkheadConfig
import io.github.resilience4j.bulkhead.BulkheadRegistry
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadConfig
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadRegistry
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.core.IntervalFunction
import io.github.resilience4j.micrometer.tagged.TaggedBulkheadMetrics
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics
import io.github.resilience4j.micrometer.tagged.TaggedRateLimiterMetrics
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics
import io.github.resilience4j.micrometer.tagged.TaggedThreadPoolBulkheadMetrics
import io.github.resilience4j.ratelimiter.RateLimiterConfig
import io.github.resilience4j.ratelimiter.RateLimiterRegistry
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.core.task.TaskDecorator
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.util.CollectionUtils
import org.springframework.util.StringUtils
import java.time.Duration
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.ThreadPoolExecutor

/**
 * 서킷 브레이커, 재시도, 속도 제한, 벌크헤드 레지스트리를 중앙집중식으로 구성하는 자동 설정이다.
 * 서비스 이름별 인스턴스를 선점 구성해 관측 지표와 알람을 의미 있는 식별자로 추적할 수 있도록 돕는다.
 */
@AutoConfiguration
@ConditionalOnClass(CircuitBreakerRegistry::class)
@EnableConfigurationProperties(ResilienceProperties::class)
class ResilienceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun circuitBreakerRegistry(properties: ResilienceProperties): CircuitBreakerRegistry {
        val defaultConfig = buildCircuitBreakerConfig(properties.circuitBreaker, CircuitBreakerOverrides())
        val registry = CircuitBreakerRegistry.of(defaultConfig)
        properties.services.forEach { (name: String, serviceProperties: ServiceProperties) ->
            registry.circuitBreaker(name, buildCircuitBreakerConfig(properties.circuitBreaker, serviceProperties.circuitBreaker))
        }
        return registry
    }

    @Bean
    @ConditionalOnMissingBean
    fun retryRegistry(properties: ResilienceProperties): RetryRegistry {
        val defaultConfig = buildRetryConfig(properties.retry, RetryOverrides())
        val registry = RetryRegistry.of(defaultConfig)
        properties.services.forEach { (name, serviceProperties) ->
            registry.retry(name, buildRetryConfig(properties.retry, serviceProperties.retry))
        }
        return registry
    }

    @Bean
    @ConditionalOnMissingBean
    fun rateLimiterRegistry(properties: ResilienceProperties): RateLimiterRegistry {
        val defaultConfig = buildRateLimiterConfig(properties.rateLimiter, RateLimiterOverrides())
        val registry = RateLimiterRegistry.of(defaultConfig)
        properties.services.forEach { (name, serviceProperties) ->
            registry.rateLimiter(name, buildRateLimiterConfig(properties.rateLimiter, serviceProperties.rateLimiter))
        }
        return registry
    }

    @Bean
    @ConditionalOnMissingBean
    fun semaphoreBulkheadRegistry(properties: ResilienceProperties): BulkheadRegistry {
        val defaultConfig = buildSemaphoreBulkheadConfig(properties.semaphoreBulkhead, SemaphoreBulkheadOverrides())
        val registry = BulkheadRegistry.of(defaultConfig)
        properties.services.forEach { (name, serviceProperties) ->
            registry.bulkhead(name, buildSemaphoreBulkheadConfig(properties.semaphoreBulkhead, serviceProperties.semaphoreBulkhead))
        }
        return registry
    }

    @Bean
    @ConditionalOnMissingBean
    fun threadPoolBulkheadRegistry(properties: ResilienceProperties): ThreadPoolBulkheadRegistry {
        val defaultConfig = buildThreadPoolBulkheadConfig(properties.threadPoolBulkhead, ThreadPoolBulkheadOverrides())
        val registry = ThreadPoolBulkheadRegistry.of(defaultConfig)
        properties.services.forEach { (name, serviceProperties) ->
            registry.bulkhead(name, buildThreadPoolBulkheadConfig(properties.threadPoolBulkhead, serviceProperties.threadPoolBulkhead))
        }
        return registry
    }

    // 관측 지표를 수집하기 위해 각 레지스트리용 Micrometer 메트릭을 Bean 으로 노출한다.
    @Bean
    fun circuitBreakerMetrics(registry: CircuitBreakerRegistry): TaggedCircuitBreakerMetrics {
        return TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry)
    }

    @Bean
    fun retryMetrics(registry: RetryRegistry): TaggedRetryMetrics {
        return TaggedRetryMetrics.ofRetryRegistry(registry)
    }

    @Bean
    fun rateLimiterMetrics(registry: RateLimiterRegistry): TaggedRateLimiterMetrics {
        return TaggedRateLimiterMetrics.ofRateLimiterRegistry(registry)
    }

    @Bean
    fun semaphoreBulkheadMetrics(registry: BulkheadRegistry): TaggedBulkheadMetrics {
        return TaggedBulkheadMetrics.ofBulkheadRegistry(registry)
    }

    @Bean
    fun threadPoolBulkheadMetrics(registry: ThreadPoolBulkheadRegistry): TaggedThreadPoolBulkheadMetrics {
        return TaggedThreadPoolBulkheadMetrics.ofThreadPoolBulkheadRegistry(registry)
    }

    // I/O 집중 작업을 위한 스레드풀을 별도로 구성해 블로킹 I/O가 전체 처리량을 방해하지 않도록 한다.
    @Bean
    @Primary
    @ConditionalOnMissingBean(name = ["ioBoundExecutor"])
    fun ioBoundExecutor(
        properties: ResilienceProperties,
        taskDecoratorProvider: ObjectProvider<TaskDecorator>
    ): ThreadPoolTaskExecutor {
        return buildExecutor(properties.threadPool.io, "researchex-io-", taskDecoratorProvider)
    }

    // CPU 연산에 집중된 작업을 전용 풀에서 실행해 컨텍스트 스위칭 비용을 최적화한다.
    @Bean
    @ConditionalOnMissingBean(name = ["cpuBoundExecutor"])
    fun cpuBoundExecutor(
        properties: ResilienceProperties,
        taskDecoratorProvider: ObjectProvider<TaskDecorator>
    ): ThreadPoolTaskExecutor {
        return buildExecutor(properties.threadPool.cpu, "researchex-cpu-", taskDecoratorProvider)
    }

    // 쓰레드 전환 시 MDC를 유지해 로깅 컨텍스트 손실을 방지한다.
    @Bean
    @ConditionalOnMissingBean
    fun taskDecorator(): TaskDecorator = MdcPropagatingTaskDecorator()

    private fun buildCircuitBreakerConfig(
        defaults: CircuitBreakerProperties,
        overrides: CircuitBreakerOverrides
    ): CircuitBreakerConfig {
        val failureRateThreshold = pick(overrides.failureRateThreshold, defaults.failureRateThreshold)
        val slowCallRateThreshold = pick(overrides.slowCallRateThreshold, defaults.slowCallRateThreshold)
        val slowCallDuration = pick(overrides.slowCallDuration, defaults.slowCallDuration)
        val waitDuration = pick(overrides.waitDurationInOpenState, defaults.waitDurationInOpenState)
        val halfOpenCalls = pick(overrides.permittedNumberOfCallsInHalfOpenState, defaults.permittedNumberOfCallsInHalfOpenState)
        val autoTransition = pick(overrides.automaticTransitionFromOpenToHalfOpenEnabled, defaults.automaticTransitionFromOpenToHalfOpenEnabled)
        val minimumNumberOfCalls = pick(overrides.minimumNumberOfCalls, defaults.minimumNumberOfCalls)
        val slidingWindowSize = pick(overrides.slidingWindowSize, defaults.slidingWindowSize)
        val windowType = pick(overrides.slidingWindowType, defaults.slidingWindowType)
        val recordExceptionNames = pick(overrides.recordExceptions, defaults.recordExceptions)
        val ignoreExceptionNames = pick(overrides.ignoreExceptions, defaults.ignoreExceptions)

        val builder = CircuitBreakerConfig.custom()
            .failureRateThreshold(failureRateThreshold)
            .slowCallRateThreshold(slowCallRateThreshold)
            .slowCallDurationThreshold(slowCallDuration)
            .waitDurationInOpenState(waitDuration)
            .minimumNumberOfCalls(minimumNumberOfCalls)
            .permittedNumberOfCallsInHalfOpenState(halfOpenCalls)
            .automaticTransitionFromOpenToHalfOpenEnabled(autoTransition)
            .slidingWindowSize(slidingWindowSize)
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.valueOf(windowType.name))

        applyExceptionConfiguration(builder, recordExceptionNames, ignoreExceptionNames)
        return builder.build()
    }

    private fun buildRetryConfig(
        defaults: RetryProperties,
        overrides: RetryOverrides
    ): RetryConfig {
        val maxAttempts = pick(overrides.maxAttempts, defaults.maxAttempts)
        val initialInterval = pick(overrides.initialInterval, defaults.initialInterval)
        val multiplier = pick(overrides.multiplier, defaults.multiplier)
        val maxInterval = pick(overrides.maxInterval, defaults.maxInterval)
        val jitter = pick(overrides.randomJitter, defaults.randomJitter)
        val retryExceptionNames = pick(overrides.retryExceptions, defaults.retryExceptions)
        val ignoreExceptionNames = pick(overrides.ignoreExceptions, defaults.ignoreExceptions)

        val intervalFunction = IntervalFunction { attempt ->
            exponentialBackoff(initialInterval, multiplier, maxInterval, jitter, attempt)
        }

        val builder = RetryConfig.custom<Any>()
            .maxAttempts(maxAttempts)
            .intervalFunction(intervalFunction)

        applyRetryExceptionConfiguration(builder, retryExceptionNames, ignoreExceptionNames)
        return builder.build()
    }

    private fun exponentialBackoff(
        initialInterval: Duration,
        multiplier: Double,
        maxInterval: Duration,
        jitter: Duration,
        attempt: Int
    ): Long {
        val computed = initialInterval.toMillis() * Math.pow(multiplier, (attempt - 1).coerceAtLeast(0).toDouble())
        val capped = computed.coerceAtMost(maxInterval.toMillis().toDouble()).toLong()
        if (!jitter.isZero && jitter.toMillis() > 0) {
            val tolerance = jitter.toMillis()
            val delta = ThreadLocalRandom.current().nextLong(-tolerance, tolerance + 1)
            val candidate = capped + delta
            return candidate.coerceAtLeast(0)
        }
        return capped.coerceAtLeast(0)
    }

    private fun buildRateLimiterConfig(
        defaults: RateLimiterProperties,
        overrides: RateLimiterOverrides
    ): RateLimiterConfig {
        val limitForPeriod = pick(overrides.limitForPeriod, defaults.limitForPeriod)
        val refreshPeriod = pick(overrides.limitRefreshPeriod, defaults.limitRefreshPeriod)
        val timeout = pick(overrides.timeoutDuration, defaults.timeoutDuration)
        val drainPermissions = pick(overrides.drainPermissionsOnResult, defaults.drainPermissionsOnResult)

        return RateLimiterConfig.custom()
            .limitForPeriod(limitForPeriod)
            .limitRefreshPeriod(refreshPeriod)
            .timeoutDuration(timeout)
            .apply { drainPermissionsOnResult { drainPermissions } }
            .build()
    }

    private fun buildSemaphoreBulkheadConfig(
        defaults: SemaphoreBulkheadProperties,
        overrides: SemaphoreBulkheadOverrides
    ): BulkheadConfig {
        val maxConcurrentCalls = pick(overrides.maxConcurrentCalls, defaults.maxConcurrentCalls)
        val maxWait = pick(overrides.maxWaitDuration, defaults.maxWaitDuration)
        val fairCallHandling = pick(overrides.fairCallHandling, defaults.fairCallHandling)

        return BulkheadConfig.custom()
            .maxConcurrentCalls(maxConcurrentCalls)
            .maxWaitDuration(maxWait)
            .fairCallHandlingStrategyEnabled(fairCallHandling)
            .build()
    }

    private fun buildThreadPoolBulkheadConfig(
        defaults: ThreadPoolBulkheadProperties,
        overrides: ThreadPoolBulkheadOverrides
    ): ThreadPoolBulkheadConfig {
        val corePoolSize = pick(overrides.coreThreadPoolSize, defaults.coreThreadPoolSize)
        val maxPoolSize = pick(overrides.maxThreadPoolSize, defaults.maxThreadPoolSize)
        val queueCapacity = pick(overrides.queueCapacity, defaults.queueCapacity)
        val keepAlive = pick(overrides.keepAliveDuration, defaults.keepAliveDuration)

        return ThreadPoolBulkheadConfig.custom()
            .coreThreadPoolSize(corePoolSize)
            .maxThreadPoolSize(maxPoolSize)
            .queueCapacity(queueCapacity)
            .keepAliveDuration(keepAlive)
            .build()
    }

    private fun buildExecutor(
        properties: ExecutorProperties,
        threadNamePrefix: String,
        taskDecoratorProvider: ObjectProvider<TaskDecorator>
    ): ThreadPoolTaskExecutor {
        val executor = ThreadPoolTaskExecutor()
        executor.threadNamePrefix = threadNamePrefix
        executor.corePoolSize = properties.corePoolSize
        executor.maxPoolSize = properties.maxPoolSize
        executor.setQueueCapacity(properties.queueCapacity)
        executor.setKeepAliveSeconds(properties.keepAlive.seconds.toInt())
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.setAwaitTerminationSeconds(Duration.ofSeconds(30).seconds.toInt())
        executor.setTaskDecorator(taskDecoratorProvider.getIfAvailable { MdcPropagatingTaskDecorator() })
        executor.setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
        executor.afterPropertiesSet()
        return executor
    }

    private fun applyExceptionConfiguration(
        builder: CircuitBreakerConfig.Builder,
        recordExceptionNames: List<String>,
        ignoreExceptionNames: List<String>
    ) {
        val recordClasses = resolveThrowableClasses(recordExceptionNames)
        if (recordClasses.isNotEmpty()) {
            builder.recordExceptions(*recordClasses.toTypedArray())
        }
        val ignoreClasses = resolveThrowableClasses(ignoreExceptionNames)
        if (ignoreClasses.isNotEmpty()) {
            builder.ignoreExceptions(*ignoreClasses.toTypedArray())
        }
    }

    private fun applyRetryExceptionConfiguration(
        builder: RetryConfig.Builder<Any>,
        retryExceptionNames: List<String>,
        ignoreExceptionNames: List<String>
    ) {
        val retryClasses = resolveThrowableClasses(retryExceptionNames)
        val ignoreClasses = resolveThrowableClasses(ignoreExceptionNames)
        if (retryClasses.isNotEmpty()) {
            builder.retryOnException { ex -> isInstanceOfAny(ex, retryClasses) && !isInstanceOfAny(ex, ignoreClasses) }
        } else if (ignoreClasses.isNotEmpty()) {
            builder.retryOnException { ex -> !isInstanceOfAny(ex, ignoreClasses) }
        }
    }

    private fun isInstanceOfAny(ex: Throwable, candidates: List<Class<out Throwable>>): Boolean {
        candidates.forEach { candidate ->
            if (candidate.isInstance(ex)) {
                return true
            }
        }
        return false
    }

    private fun resolveThrowableClasses(names: List<String>): List<Class<out Throwable>> {
        if (CollectionUtils.isEmpty(names)) {
            return emptyList()
        }
        val classes = mutableListOf<Class<out Throwable>>()
        names.forEach { className ->
            if (!StringUtils.hasText(className)) {
                return@forEach
            }
            val resolved = try {
                Class.forName(className)
            } catch (ex: ClassNotFoundException) {
                throw IllegalArgumentException("Unable to locate exception class: $className", ex)
            }
            if (!Throwable::class.java.isAssignableFrom(resolved)) {
                throw IllegalArgumentException("Configured class is not a Throwable: $className")
            }
            @Suppress("UNCHECKED_CAST")
            val throwableClass = resolved as Class<out Throwable>
            classes.add(throwableClass)
        }
        return classes
    }

    private fun <T> pick(override: T?, fallback: T): T = override ?: fallback
}
