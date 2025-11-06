package com.researchex.common.resilience

import io.github.resilience4j.bulkhead.BulkheadRegistry
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadRegistry
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.core.functions.Either
import io.github.resilience4j.ratelimiter.RateLimiterRegistry
import io.github.resilience4j.retry.RetryRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

/**
 * 회복탄력성 자동 설정이 기대한 레지스트리와 실행기를 노출하는지 검증한다.
 */
class ResilienceAutoConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ResilienceAutoConfiguration::class.java))

    @Test
    fun defaultConfigurationShouldExposeRegistriesAndExecutors() {
        contextRunner.run { context ->
            assertThat(context).hasSingleBean(CircuitBreakerRegistry::class.java)
            assertThat(context).hasSingleBean(RetryRegistry::class.java)
            assertThat(context).hasSingleBean(RateLimiterRegistry::class.java)
            assertThat(context).hasSingleBean(BulkheadRegistry::class.java)
            assertThat(context).hasSingleBean(ThreadPoolBulkheadRegistry::class.java)
            assertThat(context).hasBean("ioBoundExecutor")
            assertThat(context).hasBean("cpuBoundExecutor")

            val circuitBreakerRegistry = context.getBean(CircuitBreakerRegistry::class.java)
            val circuitBreaker = circuitBreakerRegistry.circuitBreaker("research-service")
            assertThat(circuitBreaker.circuitBreakerConfig.failureRateThreshold).isEqualTo(50.0f)

            val retry = context.getBean(RetryRegistry::class.java).retry("research-service")
            assertThat(retry.retryConfig.maxAttempts).isEqualTo(3)

            val rateLimiter = context.getBean(RateLimiterRegistry::class.java).rateLimiter("research-service")
            assertThat(rateLimiter.rateLimiterConfig.limitForPeriod).isEqualTo(50)

            val semaphoreBulkhead = context.getBean(BulkheadRegistry::class.java).bulkhead("research-service")
            assertThat(semaphoreBulkhead.bulkheadConfig.maxConcurrentCalls).isEqualTo(25)

            val threadPoolBulkhead = context.getBean(ThreadPoolBulkheadRegistry::class.java).bulkhead("research-service")
            assertThat(threadPoolBulkhead.bulkheadConfig.coreThreadPoolSize).isEqualTo(16)

            val ioExecutor = context.getBean("ioBoundExecutor", ThreadPoolTaskExecutor::class.java)
            val target = ioExecutor.threadPoolExecutor
            assertThat(target.corePoolSize).isEqualTo(32)
            assertThat(target.maximumPoolSize).isEqualTo(128)
        }
    }

    @Test
    fun serviceOverridesShouldApplyToNamedRegistries() {
        contextRunner
            .withPropertyValues(
                "researchex.resilience.services.deid.circuit-breaker.failure-rate-threshold=35",
                "researchex.resilience.services.deid.circuit-breaker.permitted-number-of-calls-in-half-open-state=5",
                "researchex.resilience.services.deid.retry.max-attempts=5",
                "researchex.resilience.services.deid.rate-limiter.limit-for-period=25",
                "researchex.resilience.services.deid.semaphore-bulkhead.max-concurrent-calls=12",
                "researchex.resilience.services.deid.thread-pool-bulkhead.core-thread-pool-size=4",
                "researchex.resilience.thread-pool.io.core-pool-size=8",
                "researchex.resilience.thread-pool.io.max-pool-size=16",
                "researchex.resilience.thread-pool.io.queue-capacity=250",
                "researchex.resilience.retry.initial-interval=500ms",
                "researchex.resilience.retry.multiplier=1.5",
                "researchex.resilience.retry.max-interval=5s",
                "researchex.resilience.retry.random-jitter=200ms"
            )
            .run { context ->
                val deidBreaker = context.getBean(CircuitBreakerRegistry::class.java).circuitBreaker("deid")
                assertThat(deidBreaker.circuitBreakerConfig.failureRateThreshold).isEqualTo(35.0f)
                assertThat(deidBreaker.circuitBreakerConfig.permittedNumberOfCallsInHalfOpenState).isEqualTo(5)

                val deidRetry = context.getBean(RetryRegistry::class.java).retry("deid")
                assertThat(deidRetry.retryConfig.maxAttempts).isEqualTo(5)
                val firstBackoffMillis = deidRetry.retryConfig.intervalBiFunction
                    .apply(1, Either.right<Throwable, Any?>(null))
                val secondBackoffMillis = deidRetry.retryConfig.intervalBiFunction
                    .apply(2, Either.right<Throwable, Any?>(null))
                assertThat(firstBackoffMillis).isGreaterThanOrEqualTo(300L)
                assertThat(secondBackoffMillis).isLessThanOrEqualTo(5_000L)

                val rateLimiter = context.getBean(RateLimiterRegistry::class.java).rateLimiter("deid")
                assertThat(rateLimiter.rateLimiterConfig.limitForPeriod).isEqualTo(25)

                val semaphoreBulkhead = context.getBean(BulkheadRegistry::class.java).bulkhead("deid")
                assertThat(semaphoreBulkhead.bulkheadConfig.maxConcurrentCalls).isEqualTo(12)

                val threadPoolBulkhead = context.getBean(ThreadPoolBulkheadRegistry::class.java).bulkhead("deid")
                assertThat(threadPoolBulkhead.bulkheadConfig.coreThreadPoolSize).isEqualTo(4)

                val ioExecutor = context.getBean("ioBoundExecutor", ThreadPoolTaskExecutor::class.java)
                val target = ioExecutor.threadPoolExecutor
                assertThat(target.corePoolSize).isEqualTo(8)
                assertThat(target.maximumPoolSize).isEqualTo(16)
                val queue = target.queue
                assertThat(queue.remainingCapacity() + queue.size).isEqualTo(250)
            }
    }
}
