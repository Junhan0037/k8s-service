package com.researchex.research.application;

import com.researchex.contract.deid.DeidJobEvent;
import com.researchex.contract.deid.DeidStage;
import com.researchex.research.domain.ResearchIndexDocument;
import com.researchex.research.infrastructure.InMemoryResearchIndexRepository;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.time.Instant;
import java.util.UUID;

/** 가명화 완료 이벤트를 받아 연구 인덱스를 최신 상태로 유지한다. */
@Service
public class ResearchIndexService {

  private static final Logger log = LoggerFactory.getLogger(ResearchIndexService.class);

  private final InMemoryResearchIndexRepository repository;
  private final Scheduler researchCpuScheduler;
  private final ResearchIndexCacheService cacheService;

  public ResearchIndexService(
      InMemoryResearchIndexRepository repository,
      Scheduler researchCpuScheduler,
      ResearchIndexCacheService cacheService) {
    this.repository = repository;
    this.researchCpuScheduler = researchCpuScheduler;
    this.cacheService = cacheService;
  }

  /** Deid Job 이벤트를 처리해 인덱스를 업데이트한다. */
  @Observed(
      name = "researchex.research.deid.indexing",
      contextualName = "deid-indexing",
      lowCardinalityKeyValues = {"stage", "completed"})
  public Mono<Void> handleDeidJobEvent(DeidJobEvent event) {
    if (event.getStage() != DeidStage.COMPLETED) {
      log.debug("Deid 이벤트가 COMPLETED 단계가 아니므로 인덱싱을 건너뜁니다. stage={}", event.getStage());
      return Mono.empty();
    }

    return Mono.fromCallable(() -> mapToDocument(event))
        .subscribeOn(researchCpuScheduler)
        .flatMap(repository::save)
        .doOnSuccess(
            document ->
                log.info(
                    "연구 인덱스가 갱신되었습니다. tenantId={}, jobId={}, documentId={}",
                    document.tenantId(),
                    document.jobId(),
                    document.documentId()))
        .doOnSuccess(document -> cacheService.evictDocument(document.documentId()))
        .then();
  }

  private ResearchIndexDocument mapToDocument(DeidJobEvent event) {
    String documentId = UUID.randomUUID().toString();
    return new ResearchIndexDocument(documentId, event.getTenantId(), event.getJobId(), event.getPayloadLocation(), Instant.now());
  }
}
