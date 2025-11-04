package com.researchex.common.resilience;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.bulkhead.ThreadPoolBulkhead;
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.functions.Either;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 회복탄력성 자동 설정이 기대한 레지스트리와 실행기를 노출하는지 검증한다.
 */
class ResilienceAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(
                    ResilienceAutoConfiguration.class));

    @Test
    void defaultConfigurationShouldExposeRegistriesAndExecutors() {
        contextRunner.run(context -> {
            // 기본 설정만으로도 모든 레지스트리와 실행기가 1개씩 등록되는지 확인한다.
            assertThat(context).hasSingleBean(CircuitBreakerRegistry.class);
            assertThat(context).hasSingleBean(RetryRegistry.class);
            assertThat(context).hasSingleBean(RateLimiterRegistry.class);
            assertThat(context).hasSingleBean(BulkheadRegistry.class);
            assertThat(context).hasSingleBean(ThreadPoolBulkheadRegistry.class);
            assertThat(context).hasBean("ioBoundExecutor");
            assertThat(context).hasBean("cpuBoundExecutor");

            CircuitBreakerRegistry circuitBreakerRegistry =
                    context.getBean(CircuitBreakerRegistry.class);
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("research-service");
            assertThat(circuitBreaker.getCircuitBreakerConfig().getFailureRateThreshold())
                    .isEqualTo(50.0f);

            Retry retry = context.getBean(RetryRegistry.class).retry("research-service");
            assertThat(retry.getRetryConfig().getMaxAttempts()).isEqualTo(3);

            RateLimiter rateLimiter = context.getBean(RateLimiterRegistry.class).rateLimiter("research-service");
            assertThat(rateLimiter.getRateLimiterConfig().getLimitForPeriod()).isEqualTo(50);

            Bulkhead semaphoreBulkhead =
                    context.getBean(BulkheadRegistry.class).bulkhead("research-service");
            assertThat(semaphoreBulkhead.getBulkheadConfig().getMaxConcurrentCalls()).isEqualTo(25);

            ThreadPoolBulkhead threadPoolBulkhead =
                    context.getBean(ThreadPoolBulkheadRegistry.class).bulkhead("research-service");
            assertThat(threadPoolBulkhead.getBulkheadConfig().getCoreThreadPoolSize()).isEqualTo(16);

            ThreadPoolTaskExecutor ioExecutor = context.getBean("ioBoundExecutor", ThreadPoolTaskExecutor.class);
            ThreadPoolExecutor target = ioExecutor.getThreadPoolExecutor();
            assertThat(target.getCorePoolSize()).isEqualTo(32);
            assertThat(target.getMaximumPoolSize()).isEqualTo(128);
        });
    }

    @Test
    void serviceOverridesShouldApplyToNamedRegistries() {
        contextRunner
                .withPropertyValues(
                        // De-id 파이프라인을 예로 들어 서비스별 오버라이드가 반영되는지 점검한다.
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
                        "researchex.resilience.retry.random-jitter=200ms")
                .run(context -> {
                    CircuitBreaker deidBreaker =
                            context.getBean(CircuitBreakerRegistry.class).circuitBreaker("deid");
                    assertThat(deidBreaker.getCircuitBreakerConfig().getFailureRateThreshold())
                            .isEqualTo(35.0f);
                    assertThat(deidBreaker.getCircuitBreakerConfig().getPermittedNumberOfCallsInHalfOpenState())
                            .isEqualTo(5);

                    Retry deidRetry = context.getBean(RetryRegistry.class).retry("deid");
                    assertThat(deidRetry.getRetryConfig().getMaxAttempts()).isEqualTo(5);
                    long firstBackoffMillis = deidRetry.getRetryConfig()
                            .getIntervalBiFunction()
                            .apply(1, Either.right(null));
                    long secondBackoffMillis = deidRetry.getRetryConfig()
                            .getIntervalBiFunction()
                            .apply(2, Either.right(null));
                    assertThat(firstBackoffMillis).isGreaterThanOrEqualTo(300L);
                    assertThat(secondBackoffMillis).isLessThanOrEqualTo(5000L);

                    RateLimiter rateLimiter =
                            context.getBean(RateLimiterRegistry.class).rateLimiter("deid");
                    assertThat(rateLimiter.getRateLimiterConfig().getLimitForPeriod()).isEqualTo(25);

                    Bulkhead semaphoreBulkhead =
                            context.getBean(BulkheadRegistry.class).bulkhead("deid");
                    assertThat(semaphoreBulkhead.getBulkheadConfig().getMaxConcurrentCalls()).isEqualTo(12);

                    ThreadPoolBulkhead threadPoolBulkhead =
                            context.getBean(ThreadPoolBulkheadRegistry.class).bulkhead("deid");
                    assertThat(threadPoolBulkhead.getBulkheadConfig().getCoreThreadPoolSize()).isEqualTo(4);

                    ThreadPoolTaskExecutor ioExecutor =
                            context.getBean("ioBoundExecutor", ThreadPoolTaskExecutor.class);
                    ThreadPoolExecutor target = ioExecutor.getThreadPoolExecutor();
                    assertThat(target.getCorePoolSize()).isEqualTo(8);
                    assertThat(target.getMaximumPoolSize()).isEqualTo(16);
                    assertThat(target.getQueue().remainingCapacity() + target.getQueue().size()).isEqualTo(250);
                });
    }
}
