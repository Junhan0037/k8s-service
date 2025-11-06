package com.researchex.platform.cache

import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.Duration

/**
 * 다단 캐시 동작에 필요한 기본 정책을 외부 설정으로 노출한다. TTL 및 캐시 용량은 서비스 규모에 따라 쉽게
 * 조정될 수 있으므로 기본값을 제공하되 필요 시 프로퍼티로 오버라이드할 수 있도록 구성한다.
 */
@Validated
@ConfigurationProperties(prefix = "researchex.cache")
class CacheProperties {

    @field:Valid
    var staticTier: Tier = Tier.withDefaults(Duration.ofHours(1), 512)

    @field:Valid
    var dynamicTier: Tier = Tier.withDefaults(Duration.ofMinutes(10), 2048)

    /** Redis 캐시 엔트리 네임스페이스를 통일하기 위한 기본 키 프리픽스. */
    @field:NotNull
    var redisKeyPrefix: String = "researchex::cache::"

    /** null 값 캐싱은 캐시 오염을 유발하기 쉬워 기본적으로 비활성화한다. */
    var cacheNullValues: Boolean = false

    /** Redis L2 캐시 사용 여부를 제어한다. 테스트 환경 등에서는 비활성화할 수 있다. */
    var enableRedis: Boolean = true

    /**
     * 캐시 단계별 설정을 표현하는 중첩 타입. TTL과 캐시 용량에 대한 유효성 검사를 통해 잘못된 구성을 조기에 감지한다.
     */
    class Tier {

        @field:NotNull
        var ttl: Duration = Duration.ZERO

        @field:Min(1)
        var maximumSize: Long = 1

        var recordStats: Boolean = true

        companion object {
            fun withDefaults(ttl: Duration, maximumSize: Long): Tier {
                return Tier().apply {
                    this.ttl = ttl
                    this.maximumSize = maximumSize
                }
            }
        }
    }
}
