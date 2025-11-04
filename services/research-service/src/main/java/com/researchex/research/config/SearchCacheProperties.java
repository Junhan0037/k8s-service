package com.researchex.research.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 검색 결과 캐시 구성을 외부 설정으로부터 바인딩하는 프로퍼티 클래스.
 * Redis(2차 캐시) 및 Caffeine(1차 캐시)의 핵심 파라미터를 정의한다.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "researchex.cache")
public class SearchCacheProperties {

    /**
     * 정적 메타데이터 캐시 정책.
     * 장기간 변하지 않는 도움말/코드값을 다단 캐시로 보관한다.
     */
    @Valid
    private TierProperties staticTier = new TierProperties(Duration.ofHours(1), 512);

    /**
     * 동적 검색 결과 캐시 정책.
     * 조회 빈도가 높은 질의를 짧은 TTL로 캐싱해 응답 속도를 높인다.
     */
    @Valid
    private TierProperties dynamicTier = new TierProperties(Duration.ofMinutes(10), 2048);

    /**
     * Redis 키 프리픽스는 서비스 간 충돌을 막고 키 공간을 명확히 나누기 위해 사용한다.
     */
    @NotBlank
    private String redisKeyPrefix = "researchex::cache::";

    /**
     * 캐시 단계별 공통 속성 정의.
     */
    public record TierProperties(
            @NotNull Duration ttl,
            @Positive long maximumSize
    ) {}
}
