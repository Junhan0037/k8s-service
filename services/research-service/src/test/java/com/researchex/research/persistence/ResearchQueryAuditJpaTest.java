package com.researchex.research.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Postgres Testcontainer를 활용한 DataJpa 슬라이스 테스트.
 * 스키마 매핑 및 정렬 쿼리를 검증해 17단계 테스트 전략의 JPA 계층 요구사항을 충족한다.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ResearchQueryAuditJpaTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private ResearchQueryAuditRepository repository;

    @Test
    void saveAndRetrieveMostRecentAudits() {
        // given: 서로 다른 실행 시점을 가진 감사 로그를 저장한다.
        Instant now = Instant.now();
        repository.saveAll(List.of(
                new ResearchQueryAuditEntity("tenant-1", "cancer", now.minusSeconds(60)),
                new ResearchQueryAuditEntity("tenant-1", "cardio", now.minusSeconds(30)),
                new ResearchQueryAuditEntity("tenant-1", "neuro", now)
        ));

        repository.flush();

        // when: 가장 최근 5개의 로그를 조회한다.
        List<ResearchQueryAuditEntity> recentAudits = repository.findTop5ByTenantIdOrderByExecutedAtDesc("tenant-1");

        // then: 최신 순으로 정렬되어 반환되는지 검증한다.
        assertThat(recentAudits).hasSize(3);
        assertThat(recentAudits.get(0).getQueryText()).isEqualTo("neuro");
        assertThat(recentAudits.get(1).getQueryText()).isEqualTo("cardio");
        assertThat(recentAudits.get(2).getQueryText()).isEqualTo("cancer");
    }
}
