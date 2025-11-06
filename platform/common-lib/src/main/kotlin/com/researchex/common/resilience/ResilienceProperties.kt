package com.researchex.common.resilience

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import java.time.Duration
import java.util.LinkedHashMap

/**
 * 회복탄력성 공통 설정 트리를 정의한다. 서킷 브레이커, 재시도, 속도 제한, 벌크헤드, 스레드 풀 등 주요 패턴에 대해
 * 합리적인 기본값을 제공하고, 서비스 키별 맵 구조로 세밀한 오버라이드를 지원한다.
 */
@ConfigurationProperties(prefix = "researchex.resilience")
class ResilienceProperties {

    var circuitBreaker: CircuitBreakerProperties = CircuitBreakerProperties()
    var retry: RetryProperties = RetryProperties()
    var rateLimiter: RateLimiterProperties = RateLimiterProperties()
    var semaphoreBulkhead: SemaphoreBulkheadProperties = SemaphoreBulkheadProperties()
    var threadPoolBulkhead: ThreadPoolBulkheadProperties = ThreadPoolBulkheadProperties()
    var threadPool: ThreadPoolGroup = ThreadPoolGroup()

    /**
     * 서비스 식별자(다운스트림 이름, 토픽 등)별로 세부 설정을 덮어쓰기 위한 맵이다.
     * 명시된 항목만 덮어쓰고 나머지는 전역 기본값을 따른다.
     */
    var services: MutableMap<String, ServiceProperties> = LinkedHashMap()

    /** 서킷 브레이커 전역 기본값을 정의한다. */
    class CircuitBreakerProperties {
        var failureRateThreshold: Float = 50.0f
        var slowCallRateThreshold: Float = 50.0f
        var slowCallDuration: Duration = Duration.ofSeconds(2)
        var waitDurationInOpenState: Duration = Duration.ofSeconds(10)
        var permittedNumberOfCallsInHalfOpenState: Int = 3
        var automaticTransitionFromOpenToHalfOpenEnabled: Boolean = true
        var minimumNumberOfCalls: Int = 20
        var slidingWindowSize: Int = 100
        var slidingWindowType: SlidingWindowType = SlidingWindowType.COUNT_BASED
        var recordExceptions: List<String> = listOf("java.io.IOException")
        var ignoreExceptions: List<String> = emptyList()

        enum class SlidingWindowType {
            COUNT_BASED,
            TIME_BASED
        }
    }

    /** 재시도 전역 기본값을 정의한다. */
    class RetryProperties {
        var maxAttempts: Int = 3
        var initialInterval: Duration = Duration.ofMillis(200)
        var multiplier: Double = 2.0
        var maxInterval: Duration = Duration.ofSeconds(2)
        var randomJitter: Duration = Duration.ZERO
        var retryExceptions: List<String> = listOf("java.io.IOException")
        var ignoreExceptions: List<String> = emptyList()
    }

    /** 속도 제한 전역 기본값을 정의한다. */
    class RateLimiterProperties {
        var limitForPeriod: Int = 50
        var limitRefreshPeriod: Duration = Duration.ofSeconds(1)
        var timeoutDuration: Duration = Duration.ofSeconds(1)
        var drainPermissionsOnResult: Boolean = false
    }

    /** 세마포어 벌크헤드 전역 기본값을 정의한다. */
    class SemaphoreBulkheadProperties {
        var maxConcurrentCalls: Int = 25
        var maxWaitDuration: Duration = Duration.ZERO
        var fairCallHandling: Boolean = false
    }

    /** 스레드 풀 벌크헤드 전역 기본값을 정의한다. */
    class ThreadPoolBulkheadProperties {
        var coreThreadPoolSize: Int = 16
        var maxThreadPoolSize: Int = 32
        var queueCapacity: Int = 200
        var keepAliveDuration: Duration = Duration.ofSeconds(60)
    }

    /** 전역 I/O 및 CPU 실행 풀 구성을 묶어 제공한다. */
    class ThreadPoolGroup {
        @NestedConfigurationProperty
        var io: ExecutorProperties = ExecutorProperties.forIoPool()

        @NestedConfigurationProperty
        var cpu: ExecutorProperties = ExecutorProperties.forCpuPool()
    }

    /** ThreadPoolExecutor에 매핑될 실행자 속성을 캡슐화한다. */
    class ExecutorProperties {
        var corePoolSize: Int = 0
        var maxPoolSize: Int = 0
        var queueCapacity: Int = 0
        var keepAlive: Duration = Duration.ofSeconds(60)

        companion object {
            fun forIoPool(): ExecutorProperties {
                val props = ExecutorProperties()
                props.corePoolSize = 32
                props.maxPoolSize = 128
                props.queueCapacity = 1000
                props.keepAlive = Duration.ofSeconds(120)
                return props
            }

            fun forCpuPool(): ExecutorProperties {
                val availableProcessors = Runtime.getRuntime().availableProcessors()
                val core = maxOf(4, availableProcessors)
                val max = core * 2
                val props = ExecutorProperties()
                props.corePoolSize = core
                props.maxPoolSize = max
                props.queueCapacity = 200
                props.keepAlive = Duration.ofSeconds(60)
                return props
            }
        }
    }

    /** 서비스별 패턴 오버라이드 구성을 보유한다. */
    class ServiceProperties {
        @NestedConfigurationProperty
        var circuitBreaker: CircuitBreakerOverrides = CircuitBreakerOverrides()

        @NestedConfigurationProperty
        var retry: RetryOverrides = RetryOverrides()

        @NestedConfigurationProperty
        var rateLimiter: RateLimiterOverrides = RateLimiterOverrides()

        @NestedConfigurationProperty
        var semaphoreBulkhead: SemaphoreBulkheadOverrides = SemaphoreBulkheadOverrides()

        @NestedConfigurationProperty
        var threadPoolBulkhead: ThreadPoolBulkheadOverrides = ThreadPoolBulkheadOverrides()
    }

    /** 서비스 서킷 브레이커 오버라이드 값을 캡슐화한다. */
    class CircuitBreakerOverrides {
        var failureRateThreshold: Float? = null
        var slowCallRateThreshold: Float? = null
        var slowCallDuration: Duration? = null
        var waitDurationInOpenState: Duration? = null
        var permittedNumberOfCallsInHalfOpenState: Int? = null
        var automaticTransitionFromOpenToHalfOpenEnabled: Boolean? = null
        var minimumNumberOfCalls: Int? = null
        var slidingWindowSize: Int? = null
        var slidingWindowType: CircuitBreakerProperties.SlidingWindowType? = null
        var recordExceptions: List<String>? = null
        var ignoreExceptions: List<String>? = null
    }

    /** 서비스 재시도 오버라이드 값을 캡슐화한다. */
    class RetryOverrides {
        var maxAttempts: Int? = null
        var initialInterval: Duration? = null
        var multiplier: Double? = null
        var maxInterval: Duration? = null
        var randomJitter: Duration? = null
        var retryExceptions: List<String>? = null
        var ignoreExceptions: List<String>? = null
    }

    /** 서비스 속도 제한 오버라이드 값을 캡슐화한다. */
    class RateLimiterOverrides {
        var limitForPeriod: Int? = null
        var limitRefreshPeriod: Duration? = null
        var timeoutDuration: Duration? = null
        var drainPermissionsOnResult: Boolean? = null
    }

    /** 서비스 세마포어 벌크헤드 오버라이드 값을 캡슐화한다. */
    class SemaphoreBulkheadOverrides {
        var maxConcurrentCalls: Int? = null
        var maxWaitDuration: Duration? = null
        var fairCallHandling: Boolean? = null
    }

    /** 서비스 스레드 풀 벌크헤드 오버라이드 값을 캡슐화한다. */
    class ThreadPoolBulkheadOverrides {
        var coreThreadPoolSize: Int? = null
        var maxThreadPoolSize: Int? = null
        var queueCapacity: Int? = null
        var keepAliveDuration: Duration? = null
    }
}
