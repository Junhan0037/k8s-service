package com.researchex.common.resilience;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * 회복탄력성 공통 설정 트리를 정의한다. 서킷 브레이커, 재시도, 속도 제한, 벌크헤드, 스레드 풀 등 주요 패턴에 대해
 * 합리적인 기본값을 제공하고, 서비스 키별 맵 구조로 세밀한 오버라이드를 지원한다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "researchex.resilience")
public class ResilienceProperties {

    private CircuitBreakerProperties circuitBreaker = new CircuitBreakerProperties();
    private RetryProperties retry = new RetryProperties();
    private RateLimiterProperties rateLimiter = new RateLimiterProperties();
    private SemaphoreBulkheadProperties semaphoreBulkhead = new SemaphoreBulkheadProperties();
    private ThreadPoolBulkheadProperties threadPoolBulkhead = new ThreadPoolBulkheadProperties();
    private ThreadPoolGroup threadPool = new ThreadPoolGroup();

    /**
     * 서비스 식별자(다운스트림 이름, 토픽 등)별로 세부 설정을 덮어쓰기 위한 맵이다.
     * 명시된 항목만 덮어쓰고 나머지는 전역 기본값을 따른다.
     */
    private Map<String, ServiceProperties> services = new LinkedHashMap<>();

    // 서킷 브레이커 전역 기본값을 정의한다.
    @Getter
    @Setter
    public static class CircuitBreakerProperties {

        private float failureRateThreshold = 50.0f;
        private float slowCallRateThreshold = 50.0f;
        private Duration slowCallDuration = Duration.ofSeconds(2);
        private Duration waitDurationInOpenState = Duration.ofSeconds(10);
        private int permittedNumberOfCallsInHalfOpenState = 3;
        private boolean automaticTransitionFromOpenToHalfOpenEnabled = true;
        private int minimumNumberOfCalls = 20;
        private int slidingWindowSize = 100;
        private SlidingWindowType slidingWindowType = SlidingWindowType.COUNT_BASED;
        private List<String> recordExceptions = Collections.singletonList("java.io.IOException");
        private List<String> ignoreExceptions = Collections.emptyList();

        public enum SlidingWindowType {
            COUNT_BASED,
            TIME_BASED
        }
    }

    // 재시도 전역 기본값을 정의한다.
    @Getter
    @Setter
    public static class RetryProperties {

        private int maxAttempts = 3;
        private Duration initialInterval = Duration.ofMillis(200);
        private double multiplier = 2.0d;
        private Duration maxInterval = Duration.ofSeconds(2);
        private Duration randomJitter = Duration.ZERO;
        private List<String> retryExceptions = Collections.singletonList("java.io.IOException");
        private List<String> ignoreExceptions = Collections.emptyList();
    }

    // 속도 제한 전역 기본값을 정의한다.
    @Getter
    @Setter
    public static class RateLimiterProperties {

        private int limitForPeriod = 50;
        private Duration limitRefreshPeriod = Duration.ofSeconds(1);
        private Duration timeoutDuration = Duration.ofSeconds(1);
        private boolean drainPermissionsOnResult = false;
    }

    // 세마포어 벌크헤드 전역 기본값을 정의한다.
    @Getter
    @Setter
    public static class SemaphoreBulkheadProperties {

        private int maxConcurrentCalls = 25;
        private Duration maxWaitDuration = Duration.ZERO;
        private boolean fairCallHandling = false;
    }

    // 스레드 풀 벌크헤드 전역 기본값을 정의한다.
    @Getter
    @Setter
    public static class ThreadPoolBulkheadProperties {

        private int coreThreadPoolSize = 16;
        private int maxThreadPoolSize = 32;
        private int queueCapacity = 200;
        private Duration keepAliveDuration = Duration.ofSeconds(60);
    }

    // 전역 I/O 및 CPU 실행 풀 구성을 묶어 제공한다.
    @Getter
    @Setter
    public static class ThreadPoolGroup {

        @NestedConfigurationProperty
        private ExecutorProperties io = ExecutorProperties.forIoPool();

        @NestedConfigurationProperty
        private ExecutorProperties cpu = ExecutorProperties.forCpuPool();
    }

    // ThreadPoolExecutor에 매핑될 실행자 속성을 캡슐화한다.
    @Getter
    @Setter
    public static class ExecutorProperties {

        private int corePoolSize;
        private int maxPoolSize;
        private int queueCapacity;
        private Duration keepAlive = Duration.ofSeconds(60);

        public ExecutorProperties() {}

        private ExecutorProperties(int corePoolSize, int maxPoolSize, int queueCapacity, Duration keepAlive) {
            this.corePoolSize = corePoolSize;
            this.maxPoolSize = maxPoolSize;
            this.queueCapacity = queueCapacity;
            this.keepAlive = keepAlive;
        }

        public static ExecutorProperties forIoPool() {
            return new ExecutorProperties(32, 128, 1000, Duration.ofSeconds(120));
        }

        public static ExecutorProperties forCpuPool() {
            int availableProcessors = Runtime.getRuntime().availableProcessors();
            int core = Math.max(4, availableProcessors);
            int max = core * 2;
            return new ExecutorProperties(core, max, 200, Duration.ofSeconds(60));
        }
    }

    // 서비스별 패턴 오버라이드 구성을 보유한다.
    @Getter
    @Setter
    public static class ServiceProperties {

        @NestedConfigurationProperty
        private CircuitBreakerOverrides circuitBreaker = new CircuitBreakerOverrides();

        @NestedConfigurationProperty
        private RetryOverrides retry = new RetryOverrides();

        @NestedConfigurationProperty
        private RateLimiterOverrides rateLimiter = new RateLimiterOverrides();

        @NestedConfigurationProperty
        private SemaphoreBulkheadOverrides semaphoreBulkhead = new SemaphoreBulkheadOverrides();

        @NestedConfigurationProperty
        private ThreadPoolBulkheadOverrides threadPoolBulkhead = new ThreadPoolBulkheadOverrides();
    }

    // 서비스 서킷 브레이커 오버라이드 값을 캡슐화한다.
    @Getter
    @Setter
    public static class CircuitBreakerOverrides {

        private Float failureRateThreshold;
        private Float slowCallRateThreshold;
        private Duration slowCallDuration;
        private Duration waitDurationInOpenState;
        private Integer permittedNumberOfCallsInHalfOpenState;
        private Boolean automaticTransitionFromOpenToHalfOpenEnabled;
        private Integer minimumNumberOfCalls;
        private Integer slidingWindowSize;
        private CircuitBreakerProperties.SlidingWindowType slidingWindowType;
        private List<String> recordExceptions;
        private List<String> ignoreExceptions;
    }

    // 서비스 재시도 오버라이드 값을 캡슐화한다.
    @Getter
    @Setter
    public static class RetryOverrides {

        private Integer maxAttempts;
        private Duration initialInterval;
        private Double multiplier;
        private Duration maxInterval;
        private Duration randomJitter;
        private List<String> retryExceptions;
        private List<String> ignoreExceptions;
    }

    // 서비스 속도 제한 오버라이드 값을 캡슐화한다.
    @Getter
    @Setter
    public static class RateLimiterOverrides {

        private Integer limitForPeriod;
        private Duration limitRefreshPeriod;
        private Duration timeoutDuration;
        private Boolean drainPermissionsOnResult;
    }

    // 서비스 세마포어 벌크헤드 오버라이드 값을 캡슐화한다.
    @Getter
    @Setter
    public static class SemaphoreBulkheadOverrides {

        private Integer maxConcurrentCalls;
        private Duration maxWaitDuration;
        private Boolean fairCallHandling;
    }

    // 서비스 스레드 풀 벌크헤드 오버라이드 값을 캡슐화한다.
    @Getter
    @Setter
    public static class ThreadPoolBulkheadOverrides {

        private Integer coreThreadPoolSize;
        private Integer maxThreadPoolSize;
        private Integer queueCapacity;
        private Duration keepAliveDuration;
    }
}
