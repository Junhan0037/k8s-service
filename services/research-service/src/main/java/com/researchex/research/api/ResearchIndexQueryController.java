package com.researchex.research.api;

import com.researchex.research.application.ResearchIndexQueryService;
import com.researchex.research.domain.ResearchIndexDocument;
import java.util.List;
import io.micrometer.observation.annotation.Observed;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** 로컬 개발 환경에서 인덱스 상태를 확인하기 위한 엔드포인트. */
@RestController
@RequestMapping("/internal/research/index")
public class ResearchIndexQueryController {

  private final ResearchIndexQueryService queryService;

  public ResearchIndexQueryController(ResearchIndexQueryService queryService) {
    this.queryService = queryService;
  }

  @GetMapping
  @Observed(
      name = "researchex.research.index.list",
      contextualName = "research-index-list",
      lowCardinalityKeyValues = {"endpoint", "findAll"})
  public Mono<List<ResearchIndexDocument>> findAll() {
    return queryService.findAll();
  }

  @GetMapping("/{documentId}")
  @Observed(
      name = "researchex.research.index.detail",
      contextualName = "research-index-detail",
      lowCardinalityKeyValues = {"endpoint", "findById"})
  public Mono<ResponseEntity<ResearchIndexDocument>> findByDocumentId(
      @PathVariable String documentId) {
    return queryService
        .findByDocumentId(documentId)
        .map(optional -> optional.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build()));
  }
}
