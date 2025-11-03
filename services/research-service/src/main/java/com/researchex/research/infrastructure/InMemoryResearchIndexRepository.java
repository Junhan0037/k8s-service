package com.researchex.research.infrastructure;

import com.researchex.research.domain.ResearchIndexDocument;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/** 로컬 개발 환경용 In-memory Elasticsearch 스텁. */
@Repository
public class InMemoryResearchIndexRepository {

  private final Map<String, ResearchIndexDocument> store = new ConcurrentHashMap<>();
  private final Scheduler researchIoScheduler;

  public InMemoryResearchIndexRepository(Scheduler researchIoScheduler) {
    this.researchIoScheduler = researchIoScheduler;
  }

  /** 문서 저장을 비동기적으로 수행한다. */
  public Mono<ResearchIndexDocument> save(ResearchIndexDocument document) {
    return Mono.fromCallable(
            () -> {
              store.put(document.documentId(), document);
              return document;
            })
        .subscribeOn(researchIoScheduler);
  }

  /** 문서를 조회한다. */
  public Mono<Optional<ResearchIndexDocument>> findByDocumentId(String documentId) {
    return Mono.fromCallable(() -> Optional.ofNullable(store.get(documentId)))
        .subscribeOn(researchIoScheduler);
  }

  /** 전체 문서를 반환한다. (로컬 개발 관찰용) */
  public Mono<List<ResearchIndexDocument>> findAll() {
    return Mono.fromCallable(() -> List.copyOf(store.values())).subscribeOn(researchIoScheduler);
  }
}
