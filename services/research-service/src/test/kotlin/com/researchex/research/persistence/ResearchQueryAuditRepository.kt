package com.researchex.research.persistence

import org.springframework.data.jpa.repository.JpaRepository

/**
 * DataJpa 테스트에서 사용할 전용 Repository.
 * 실제 서비스 코드에 영향을 주지 않고 슬라이스 테스트 컨텍스트에만 로딩된다.
 */
interface ResearchQueryAuditRepository : JpaRepository<ResearchQueryAuditEntity, Long> {
    fun findTop5ByTenantIdOrderByExecutedAtDesc(tenantId: String): List<ResearchQueryAuditEntity>
}
