package com.researchex.research.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * DataJpa 테스트에서 사용할 전용 Repository.
 * 실제 서비스 코드에 영향을 주지 않고 슬라이스 테스트 컨텍스트에만 로딩된다.
 */
public interface ResearchQueryAuditRepository extends JpaRepository<ResearchQueryAuditEntity, Long> {

    List<ResearchQueryAuditEntity> findTop5ByTenantIdOrderByExecutedAtDesc(String tenantId);
}
