package com.researchex.research.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 테스트 전용 JPA 엔터티.
 * 검색 질의 감사 로그를 가정하고 DataJpa 슬라이스 테스트 시 실데이터와 유사한 스키마를 제공한다.
 */
@Entity
@Table(name = "research_query_audit")
class ResearchQueryAuditEntity(
    @Column(nullable = false, length = 64)
    var tenantId: String,

    @Column(nullable = false, length = 1024)
    var queryText: String,

    @Column(nullable = false)
    var executedAt: Instant
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    protected constructor() : this("", "", Instant.EPOCH)
}
