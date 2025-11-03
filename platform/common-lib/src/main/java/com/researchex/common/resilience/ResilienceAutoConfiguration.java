package com.researchex.common.resilience;

import com.researchex.common.resilience.ResilienceProperties.CircuitBreakerOverrides;
import com.researchex.common.resilience.ResilienceProperties.CircuitBreakerProperties;
import com.researchex.common.resilience.ResilienceProperties.ExecutorProperties;
import com.researchex.common.resilience.ResilienceProperties.RateLimiterOverrides;
import com.researchex.common.resilience.ResilienceProperties.RateLimiterProperties;
import com.researchex.common.resilience.ResilienceProperties.RetryOverrides;
import com.researchex.common.resilience.ResilienceProperties.RetryProperties;
import com.researchex.common.resilience.ResilienceProperties.ThreadPoolBulkheadOverrides;
import com.researchex.common.resilience.ResilienceProperties.ThreadPoolBulkheadProperties;
import com.researchex.common.resilience.ResilienceProperties.SemaphoreBulkheadOverrides;
import com.researchex.common.resilience.ResilienceProperties.SemaphoreBulkheadProperties;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadConfig;
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.micrometer.tagged.TaggedBulkheadMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRateLimiterMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedThreadPoolBulkheadMetrics;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * 서킷 브레이커, 재시도, 속도 제한, 벌크헤드 레지스트리를 중앙집중식으로 구성하는 자동 설정이다.
 * 서비스 이름별 인스턴스를 선점 구성해 관측 지표와 알람을 의미 있는 식별자로 추적할 수 있도록 돕는다.
 */
@AutoConfiguration
@ConditionalOnClass(CircuitBreakerRegistry.class)
@EnableConfigurationProperties(ResilienceProperties.class)
public class ResilienceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CircuitBreakerRegistry circuitBreakerRegistry(ResilienceProperties properties) {
        // 전역 기본값으로 사용할 서킷 브레이커 구성을 선행 생성한다.
        CircuitBreakerConfig defaultConfig = buildCircuitBreakerConfig(properties.getCircuitBreaker(), new CircuitBreakerOverrides());
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(defaultConfig);

        // 서비스별 정의가 있을 경우 기본값 위에 덮어써 개별 특성을 반영한다.
        properties.getServices().forEach((name, serviceProperties)
                -> registry.circuitBreaker(name, buildCircuitBreakerConfig(properties.getCircuitBreaker(), serviceProperties.getCircuitBreaker())));

        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public RetryRegistry retryRegistry(ResilienceProperties properties) {
        // 공통으로 활용할 재시도 기본 구성을 마련한다.
        RetryConfig defaultConfig = buildRetryConfig(properties.getRetry(), new RetryOverrides());
        RetryRegistry registry = RetryRegistry.of(defaultConfig);

        // 개별 서비스가 재정의한 설정을 적용해 서비스명 기반 인스턴스를 초기화한다.
        properties.getServices().forEach((name, serviceProperties)
                -> registry.retry(name, buildRetryConfig(properties.getRetry(), serviceProperties.getRetry())));

        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimiterRegistry rateLimiterRegistry(ResilienceProperties properties) {
        // 전역 속도 제한 기준을 정의해 모든 인스턴스가 일관된 기본 동작을 갖도록 한다.
        RateLimiterConfig defaultConfig = buildRateLimiterConfig(properties.getRateLimiter(), new RateLimiterOverrides());
        RateLimiterRegistry registry = RateLimiterRegistry.of(defaultConfig);

        // 서비스별 요구사항을 반영해 레이트 리미터를 미리 등록한다.
        properties.getServices().forEach((name, serviceProperties)
                -> registry.rateLimiter(name, buildRateLimiterConfig(properties.getRateLimiter(), serviceProperties.getRateLimiter())));

        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public BulkheadRegistry semaphoreBulkheadRegistry(ResilienceProperties properties) {
        // 벌크헤드 기본 설정을 마련해 공통 제한 정책을 정의한다.
        BulkheadConfig defaultConfig = buildSemaphoreBulkheadConfig(properties.getSemaphoreBulkhead(), new SemaphoreBulkheadOverrides());
        BulkheadRegistry registry = BulkheadRegistry.of(defaultConfig);

        // 서비스별 최대 동시 호출 수 등을 재정의해 충돌을 방지한다.
        properties.getServices().forEach((name, serviceProperties)
                -> registry.bulkhead(name, buildSemaphoreBulkheadConfig(properties.getSemaphoreBulkhead(), serviceProperties.getSemaphoreBulkhead())));

        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public ThreadPoolBulkheadRegistry threadPoolBulkheadRegistry(ResilienceProperties properties) {
        // 스레드풀 벌크헤드에 대한 공통 파라미터를 선행 정의한다.
        ThreadPoolBulkheadConfig defaultConfig = buildThreadPoolBulkheadConfig(properties.getThreadPoolBulkhead(), new ThreadPoolBulkheadOverrides());
        ThreadPoolBulkheadRegistry registry = ThreadPoolBulkheadRegistry.of(defaultConfig);

        // 서비스별 처리량 요구에 맞춰 전용 스레드풀 벌크헤드를 초기화한다.
        properties.getServices().forEach((name, serviceProperties) ->
                registry.bulkhead(name, buildThreadPoolBulkheadConfig(properties.getThreadPoolBulkhead(), serviceProperties.getThreadPoolBulkhead())));

        return registry;
    }

    // 각 서킷 브레이커 인스턴스별로 태그된 메트릭을 마이크로미터로 노출한다.
    @Bean
    public TaggedCircuitBreakerMetrics circuitBreakerMetrics(CircuitBreakerRegistry registry) {
        return TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry);
    }

    // 재시도 레지스트리에서 메트릭을 추출해 관측 가능성을 확보한다.
    @Bean
    public TaggedRetryMetrics retryMetrics(RetryRegistry registry) {
        return TaggedRetryMetrics.ofRetryRegistry(registry);
    }

    // 레이트 리미터 동작 상황을 태그 기반 메트릭으로 내보낸다.
    @Bean
    public TaggedRateLimiterMetrics rateLimiterMetrics(RateLimiterRegistry registry) {
        return TaggedRateLimiterMetrics.ofRateLimiterRegistry(registry);
    }

    // 세마포어 벌크헤드 사용 현황을 모니터링할 수 있는 메트릭을 제공한다.
    @Bean
    public TaggedBulkheadMetrics semaphoreBulkheadMetrics(BulkheadRegistry registry) {
        return TaggedBulkheadMetrics.ofBulkheadRegistry(registry);
    }

    // 스레드풀 벌크헤드 지표를 수집해 병목 징후를 추적한다.
    @Bean
    public TaggedThreadPoolBulkheadMetrics threadPoolBulkheadMetrics(ThreadPoolBulkheadRegistry registry) {
        return TaggedThreadPoolBulkheadMetrics.ofThreadPoolBulkheadRegistry(registry);
    }

    // I/O 집중 작업을 위한 스레드풀을 별도로 구성해 블로킹 I/O가 전체 처리량을 방해하지 않도록 한다.
    @Bean
    @Primary
    @ConditionalOnMissingBean(name = "ioBoundExecutor")
    public ThreadPoolTaskExecutor ioBoundExecutor(ResilienceProperties properties, ObjectProvider<TaskDecorator> taskDecoratorProvider) {
        return buildExecutor(properties.getThreadPool().getIo(), "researchex-io-", taskDecoratorProvider);
    }

    // CPU 연산에 집중된 작업을 전용 풀에서 실행해 컨텍스트 스위칭 비용을 최적화한다.
    @Bean
    @ConditionalOnMissingBean(name = "cpuBoundExecutor")
    public ThreadPoolTaskExecutor cpuBoundExecutor(ResilienceProperties properties, ObjectProvider<TaskDecorator> taskDecoratorProvider) {
        return buildExecutor(properties.getThreadPool().getCpu(), "researchex-cpu-", taskDecoratorProvider);
    }

    // 쓰레드 전환 시 MDC를 유지해 로깅 컨텍스트 손실을 방지한다.
    @Bean
    @ConditionalOnMissingBean
    public TaskDecorator taskDecorator() {
        return new MdcPropagatingTaskDecorator();
    }

    private CircuitBreakerConfig buildCircuitBreakerConfig(CircuitBreakerProperties defaults, CircuitBreakerOverrides overrides) {
        // 필수 파라미터는 서비스별 오버라이드가 존재하면 그것을, 없으면 기본값을 사용한다.
        float failureRateThreshold = pick(overrides.getFailureRateThreshold(), defaults.getFailureRateThreshold());
        float slowCallRateThreshold = pick(overrides.getSlowCallRateThreshold(), defaults.getSlowCallRateThreshold());
        Duration slowCallDuration = pick(overrides.getSlowCallDuration(), defaults.getSlowCallDuration());
        Duration waitDuration = pick(overrides.getWaitDurationInOpenState(), defaults.getWaitDurationInOpenState());
        int halfOpenCalls = pick(overrides.getPermittedNumberOfCallsInHalfOpenState(), defaults.getPermittedNumberOfCallsInHalfOpenState());
        boolean autoTransition = pick(overrides.getAutomaticTransitionFromOpenToHalfOpenEnabled(), defaults.isAutomaticTransitionFromOpenToHalfOpenEnabled());
        int minimumNumberOfCalls = pick(overrides.getMinimumNumberOfCalls(), defaults.getMinimumNumberOfCalls());
        int slidingWindowSize = pick(overrides.getSlidingWindowSize(), defaults.getSlidingWindowSize());
        CircuitBreakerProperties.SlidingWindowType windowType = pick(overrides.getSlidingWindowType(), defaults.getSlidingWindowType());
        List<String> recordExceptionNames = pick(overrides.getRecordExceptions(), defaults.getRecordExceptions());
        List<String> ignoreExceptionNames = pick(overrides.getIgnoreExceptions(), defaults.getIgnoreExceptions());

        // 기본 동작을 정의한 후 예외별 처리 정책을 추가로 주입한다.
        CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom()
                .failureRateThreshold(failureRateThreshold)
                .slowCallRateThreshold(slowCallRateThreshold)
                .slowCallDurationThreshold(slowCallDuration)
                .waitDurationInOpenState(waitDuration)
                .minimumNumberOfCalls(minimumNumberOfCalls)
                .permittedNumberOfCallsInHalfOpenState(halfOpenCalls)
                .automaticTransitionFromOpenToHalfOpenEnabled(autoTransition)
                .slidingWindowSize(slidingWindowSize)
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.valueOf(windowType.name()));

        // 예외 처리 전략을 추가 적용해 회로 전환 조건을 세밀하게 제어한다.
        applyExceptionConfiguration(builder, recordExceptionNames, ignoreExceptionNames);
        return builder.build();
    }

    private RetryConfig buildRetryConfig(RetryProperties defaults, RetryOverrides overrides) {
        // 오버라이드 우선 규칙은 동일하게 적용한다.
        int maxAttempts = pick(overrides.getMaxAttempts(), defaults.getMaxAttempts());
        Duration initialInterval = pick(overrides.getInitialInterval(), defaults.getInitialInterval());
        double multiplier = pick(overrides.getMultiplier(), defaults.getMultiplier());
        Duration maxInterval = pick(overrides.getMaxInterval(), defaults.getMaxInterval());
        Duration jitter = pick(overrides.getRandomJitter(), defaults.getRandomJitter());
        List<String> retryExceptionNames = pick(overrides.getRetryExceptions(), defaults.getRetryExceptions());
        List<String> ignoreExceptionNames = pick(overrides.getIgnoreExceptions(), defaults.getIgnoreExceptions());

        // 지수 백오프 함수를 구성해 재시도 간격을 동적으로 계산한다.
        IntervalFunction baseFunction = attempt -> exponentialBackoff(initialInterval, multiplier, maxInterval, jitter, attempt);

        // 계산된 백오프 함수를 적용해 재시도 빌더를 완성한다.
        RetryConfig.Builder<Object> builder = RetryConfig.custom().maxAttempts(maxAttempts).intervalFunction(baseFunction);
        applyRetryExceptionConfiguration(builder, retryExceptionNames, ignoreExceptionNames);
        return builder.build();
    }

    private long exponentialBackoff(Duration initialInterval, double multiplier, Duration maxInterval, Duration jitter, int attempt) {
        // 지수 증가 폭이 설정된 최대 대기 시간으로 제한되도록 보정한다.
        double computed = initialInterval.toMillis() * Math.pow(multiplier, Math.max(0, attempt - 1));
        long capped = (long) Math.min(computed, maxInterval.toMillis());
        if (!jitter.isZero() && jitter.toMillis() > 0) {
            // 여러 노드가 동시에 재시도하지 않도록 일정 범위 내에서 무작위 지연을 추가한다.
            long tolerance = jitter.toMillis();
            long delta = ThreadLocalRandom.current().nextLong(-tolerance, tolerance + 1);
            long candidate = capped + delta;
            return Math.max(0, candidate);
        }
        return Math.max(0, capped);
    }

    private RateLimiterConfig buildRateLimiterConfig(RateLimiterProperties defaults, RateLimiterOverrides overrides) {
        int limitForPeriod = pick(overrides.getLimitForPeriod(), defaults.getLimitForPeriod());
        Duration refreshPeriod = pick(overrides.getLimitRefreshPeriod(), defaults.getLimitRefreshPeriod());
        Duration timeout = pick(overrides.getTimeoutDuration(), defaults.getTimeoutDuration());
        boolean drainPermissions =
                pick(overrides.getDrainPermissionsOnResult(), defaults.isDrainPermissionsOnResult());

        // 요청 제한량과 타임아웃을 설정하고, 필요 시 결과에 따라 권한을 소모하도록 커스터마이징한다.
        RateLimiterConfig.Builder builder = RateLimiterConfig.custom()
                .limitForPeriod(limitForPeriod)
                .limitRefreshPeriod(refreshPeriod)
                .timeoutDuration(timeout);

        // 결과에 따라 남은 퍼미션을 비우도록 조정해 실패 시 빠르게 회복할 수 있도록 한다.
        builder.drainPermissionsOnResult(either -> drainPermissions);
        return builder.build();
    }

    private BulkheadConfig buildSemaphoreBulkheadConfig(SemaphoreBulkheadProperties defaults, SemaphoreBulkheadOverrides overrides) {
        // 고정 세마포어 벌크헤드는 주요 파라미터를 오버라이드 가능하도록 제공한다.
        int maxConcurrentCalls = pick(overrides.getMaxConcurrentCalls(), defaults.getMaxConcurrentCalls());
        Duration maxWait = pick(overrides.getMaxWaitDuration(), defaults.getMaxWaitDuration());
        boolean fairCallHandling = pick(overrides.getFairCallHandling(), defaults.isFairCallHandling());

        // 공정성 전략과 허용 동시성 등을 builder에 주입한다.
        return BulkheadConfig.custom()
                .maxConcurrentCalls(maxConcurrentCalls)
                .maxWaitDuration(maxWait)
                .fairCallHandlingStrategyEnabled(fairCallHandling)
                .build();
    }

    private ThreadPoolBulkheadConfig buildThreadPoolBulkheadConfig(ThreadPoolBulkheadProperties defaults, ThreadPoolBulkheadOverrides overrides) {
        // 스레드풀 벌크헤드 구성 요소를 오버라이드 우선 규칙으로 추출한다.
        int corePoolSize = pick(overrides.getCoreThreadPoolSize(), defaults.getCoreThreadPoolSize());
        int maxPoolSize = pick(overrides.getMaxThreadPoolSize(), defaults.getMaxThreadPoolSize());
        int queueCapacity = pick(overrides.getQueueCapacity(), defaults.getQueueCapacity());
        Duration keepAlive = pick(overrides.getKeepAliveDuration(), defaults.getKeepAliveDuration());

        // 코어/최대 스레드 수와 큐 용량 등을 builder로 조합한다.
        return ThreadPoolBulkheadConfig.custom()
                .coreThreadPoolSize(corePoolSize)
                .maxThreadPoolSize(maxPoolSize)
                .queueCapacity(queueCapacity)
                .keepAliveDuration(keepAlive)
                .build();
    }

    private ThreadPoolTaskExecutor buildExecutor(ExecutorProperties properties, String threadNamePrefix, ObjectProvider<TaskDecorator> taskDecorator) {
        // 공통 스레드풀 생성을 담당하며 전달된 속성을 기반으로 일관된 정책을 적용한다.
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 모니터링 시 풀을 구분하기 위해 명명 규칙을 설정한다.
        executor.setThreadNamePrefix(threadNamePrefix);

        // 풀 크기 및 큐 용량을 속성 값에 맞춰 세밀하게 제어한다.
        executor.setCorePoolSize(properties.getCorePoolSize());
        executor.setMaxPoolSize(properties.getMaxPoolSize());
        executor.setQueueCapacity(properties.getQueueCapacity());

        // KeepAlive와 종료 시 동작을 명시적으로 정의해 자원 누수를 방지한다.
        executor.setKeepAliveSeconds((int) properties.getKeepAlive().toSeconds());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds((int) Duration.ofSeconds(30).toSeconds());

        // MDC를 보존할 수 있는 TaskDecorator를 주입해 진단 가능성을 높인다.
        executor.setTaskDecorator(taskDecorator.getIfAvailable(MdcPropagatingTaskDecorator::new));

        // CallerRunsPolicy를 사용해 작업을 버리지 않고 호출 스레드에서 백프레셔를 즉시 전달한다.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // Spring 컨텍스트와 연동될 수 있게 내부 상태를 초기화한다.
        executor.afterPropertiesSet();

        return executor;
    }

    @SuppressWarnings("unchecked")
    private void applyExceptionConfiguration(CircuitBreakerConfig.Builder builder, List<String> recordExceptionNames, List<String> ignoreExceptionNames) {
        // 문자열로 정의된 예외 목록을 실제 클래스 타입으로 변환한다.
        List<Class<? extends Throwable>> recordClasses = resolveThrowableClasses(recordExceptionNames);
        if (!recordClasses.isEmpty()) {
            // 특정 예외를 기록 대상으로 지정해 회로 상태 전환에 활용한다.
            builder.recordExceptions(recordClasses.toArray(new Class[0]));
        }

        List<Class<? extends Throwable>> ignoreClasses = resolveThrowableClasses(ignoreExceptionNames);
        if (!ignoreClasses.isEmpty()) {
            // 무시할 예외를 등록해 불필요한 상태 전환을 방지한다.
            builder.ignoreExceptions(ignoreClasses.toArray(new Class[0]));
        }
    }

    private void applyRetryExceptionConfiguration(RetryConfig.Builder<Object> builder, List<String> retryExceptionNames, List<String> ignoreExceptionNames) {
        // 재시도 대상과 예외 대상에서 제외할 항목을 각각 변환한다.
        List<Class<? extends Throwable>> retryClasses = resolveThrowableClasses(retryExceptionNames);
        List<Class<? extends Throwable>> ignoreClasses = resolveThrowableClasses(ignoreExceptionNames);
        if (!retryClasses.isEmpty()) {
            // 재시도 대상이 명시된 경우, 제외 목록과 중복되지 않는지 확인해 조건을 구성한다.
            builder.retryOnException(ex -> isInstanceOfAny(ex, retryClasses) && !isInstanceOfAny(ex, ignoreClasses));
        } else if (!ignoreClasses.isEmpty()) {
            // 재시도 대상이 없고 제외 목록만 있는 경우, 제외된 예외를 필터링한다.
            builder.retryOnException(ex -> !isInstanceOfAny(ex, ignoreClasses));
        }
    }

    private boolean isInstanceOfAny(Throwable ex, List<Class<? extends Throwable>> candidates) {
        // 주어진 예외가 후보 목록 중 하나라도 일치하는지 검사한다.
        for (Class<? extends Throwable> candidate : candidates) {
            if (candidate.isInstance(ex)) {
                return true;
            }
        }
        return false;
    }

    private List<Class<? extends Throwable>> resolveThrowableClasses(List<String> names) {
        if (CollectionUtils.isEmpty(names)) {
            return Collections.emptyList();
        }
        // 구성에 정의된 클래스 이름을 순회하며 예외 타입으로 로딩한다.
        List<Class<? extends Throwable>> classes = new ArrayList<>();
        for (String className : names) {
            if (!StringUtils.hasText(className)) {
                continue;
            }
            try {
                Class<?> resolved = Class.forName(className);
                if (!Throwable.class.isAssignableFrom(resolved)) {
                    throw new IllegalArgumentException("Configured class is not a Throwable: " + className);
                }
                @SuppressWarnings("unchecked")
                Class<? extends Throwable> throwableClass = (Class<? extends Throwable>) resolved;
                classes.add(throwableClass);
            } catch (ClassNotFoundException ex) {
                throw new IllegalArgumentException("Unable to locate exception class: " + className, ex);
            }
        }
        return classes;
    }

    // 오버라이드 값이 존재하면 우선 사용하고, 없으면 기본값을 반환한다.
    private <T> T pick(T override, T fallback) {
        return override != null ? override : fallback;
    }
}
