package com.researchex.research.progress;

import com.researchex.contract.research.ResearchQueryStatus;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/**
 * {@link ResearchProgressService} 의 SSE 스트림 동작을 검증한다.
 */
class ResearchProgressServiceTest {

    @Test
    void streamByQueryId_deliversLatestAndSubsequentEvents() {
        ResearchProgressService service = new ResearchProgressService();

        service.publishPending("tenant-alpha", "query-123", "요청 접수");

        StepVerifier.create(service.streamByQueryId("tenant-alpha", "query-123").take(2))
                .expectNextMatches(event -> event.status() == ResearchQueryStatus.PENDING)
                .then(() -> service.publishRunning("tenant-alpha", "query-123", 50.0d, null, "검색 실행 중"))
                .expectNextMatches(event -> event.status() == ResearchQueryStatus.RUNNING)
                .verifyComplete();
    }
}
