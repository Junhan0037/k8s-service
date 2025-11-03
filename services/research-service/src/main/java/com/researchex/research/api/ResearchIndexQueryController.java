package com.researchex.research.api;

import com.researchex.research.domain.ResearchIndexDocument;
import com.researchex.research.infrastructure.InMemoryResearchIndexRepository;
import java.util.List;
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

  private final InMemoryResearchIndexRepository repository;

  public ResearchIndexQueryController(InMemoryResearchIndexRepository repository) {
    this.repository = repository;
  }

  @GetMapping
  public Mono<List<ResearchIndexDocument>> findAll() {
    return repository.findAll();
  }

  @GetMapping("/{documentId}")
  public Mono<ResponseEntity<ResearchIndexDocument>> findByDocumentId(
      @PathVariable String documentId) {
    return repository
        .findByDocumentId(documentId)
        .map(optional -> optional.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build()));
  }
}
