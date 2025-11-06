package com.researchex.research.persistence;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 테스트 전용 JPA 엔터티.
 * 검색 질의 감사 로그를 가정하고 DataJpa 슬라이스 테스트 시 실데이터와 유사한 스키마를 제공한다.
 */
@Entity
@Table(name = "research_query_audit")
public class ResearchQueryAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String tenantId;

    @Column(nullable = false, length = 1024)
    private String queryText;

    @Column(nullable = false)
    private Instant executedAt;

    protected ResearchQueryAuditEntity() {
        // JPA 기본 생성자
    }

    public ResearchQueryAuditEntity(String tenantId, String queryText, Instant executedAt) {
        this.tenantId = tenantId;
        this.queryText = queryText;
        this.executedAt = executedAt;
    }

    public Long getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getQueryText() {
        return queryText;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }
}
