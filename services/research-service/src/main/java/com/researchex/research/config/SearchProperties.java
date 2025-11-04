package com.researchex.research.config;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * Elasticsearch 검색 파라미터 및 SLA 기준을 외부 설정에서 주입받아 관리한다.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "researchex.search")
public class SearchProperties {

    /**
     * 검색 대상이 되는 Elasticsearch 인덱스명.
     */
    @NotBlank
    private String indexName = "research-studies";

    /**
     * API 기본 페이지 크기. 과도한 데이터 반환으로 인한 응답 지연을 방지한다.
     */
    @Min(1)
    @Max(200)
    private int defaultPageSize = 20;

    /**
     * 단일 요청에서 허용하는 최대 페이지 크기를 제한해 메모리 사용량을 보호한다.
     */
    @Min(10)
    @Max(500)
    private int maxPageSize = 100;

    /**
     * 사용자가 지정 가능한 정렬 필드 목록. 화이트리스트 방식으로 안전하게 제한한다.
     */
    @NotEmpty
    private List<String> sortableFields = List.of("updatedAt", "createdAt", "relevance", "enrollment");

    /**
     * Elasticsearch 쿼리 타임아웃. 지연이 길어질 경우 빠르게 실패해 Graceful Degradation 을 유도한다.
     */
    @NotNull
    private Duration queryTimeout = Duration.ofSeconds(2);

    /**
     * 검색 응답 SLA(SLO). 1초 이내 응답을 목표로 모니터링한다.
     */
    @NotNull
    private Duration slaThreshold = Duration.ofSeconds(1);
}
