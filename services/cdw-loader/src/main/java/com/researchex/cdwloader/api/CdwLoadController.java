package com.researchex.cdwloader.api;

import com.researchex.cdwloader.application.CdwLoadPipelineService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** CDW 배치 적재 요청을 수신해 비동기 파이프라인을 시작한다. */
@RestController
@RequestMapping("/api/cdw/batches")
@Validated
public class CdwLoadController {

  private static final Logger log = LoggerFactory.getLogger(CdwLoadController.class);

  private final CdwLoadPipelineService pipelineService;

  public CdwLoadController(CdwLoadPipelineService pipelineService) {
    this.pipelineService = pipelineService;
  }

  /** 배치 적재 요청을 받아 즉시 비동기 파이프라인으로 위임한다. */
  @PostMapping
  public ResponseEntity<CdwBatchResponse> acceptBatch(@Valid @RequestBody CdwBatchRequest request) {
    log.info("CDW 배치 적재 요청을 수신했습니다. tenantId={}, batchId={}", request.tenantId(), request.batchId());

    CompletableFuture<Void> pipelineFuture = pipelineService.startPipeline(request);
    pipelineFuture.whenComplete(
        (ignored, throwable) -> {
          if (throwable != null) {
            log.error("CDW 배치 적재 파이프라인 처리가 실패했습니다. tenantId={}, batchId={}", request.tenantId(), request.batchId(), throwable);
          } else {
            log.info("CDW 배치 적재 파이프라인 처리가 완료되었습니다. tenantId={}, batchId={}", request.tenantId(), request.batchId());
          }
        });

    CdwBatchResponse response = new CdwBatchResponse(request.batchId(), request.tenantId(), Instant.now());

    return ResponseEntity.accepted().body(response);
  }
}
