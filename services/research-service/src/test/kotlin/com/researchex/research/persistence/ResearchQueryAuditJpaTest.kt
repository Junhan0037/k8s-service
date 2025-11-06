package com.researchex.research.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant

/**
 * Postgres Testcontainer를 활용한 DataJpa 슬라이스 테스트.
 * 스키마 매핑 및 정렬 쿼리를 검증해 17단계 테스트 전략의 JPA 계층 요구사항을 충족한다.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ResearchQueryAuditJpaTest @Autowired constructor(
    private val repository: ResearchQueryAuditRepository
) {

    @Test
    fun saveAndRetrieveMostRecentAudits() {
        val now = Instant.now()
        repository.saveAll(
            listOf(
                ResearchQueryAuditEntity("tenant-1", "cancer", now.minusSeconds(60)),
                ResearchQueryAuditEntity("tenant-1", "cardio", now.minusSeconds(30)),
                ResearchQueryAuditEntity("tenant-1", "neuro", now)
            )
        )
        repository.flush()

        val recentAudits = repository.findTop5ByTenantIdOrderByExecutedAtDesc("tenant-1")

        assertThat(recentAudits).hasSize(3)
        assertThat(recentAudits[0].queryText).isEqualTo("neuro")
        assertThat(recentAudits[1].queryText).isEqualTo("cardio")
        assertThat(recentAudits[2].queryText).isEqualTo("cancer")
    }

    companion object {
        @Container
        @JvmField
        val POSTGRES: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:15-alpine")

        @JvmStatic
        @DynamicPropertySource
        fun configureDataSource(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl)
            registry.add("spring.datasource.username", POSTGRES::getUsername)
            registry.add("spring.datasource.password", POSTGRES::getPassword)
            registry.add("spring.jpa.hibernate.ddl-auto") { "create-drop" }
        }
    }
}
