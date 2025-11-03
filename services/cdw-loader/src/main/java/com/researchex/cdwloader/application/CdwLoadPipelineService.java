package com.researchex.cdwloader.application;

import com.researchex.cdwloader.api.CdwBatchRequest;
import com.researchex.cdwloader.config.CdwLoaderTopicProperties;
import com.researchex.common.messaging.AvroMessageConverter;
import com.researchex.contract.cdw.CdwLoadEvent;
import com.researchex.contract.cdw.CdwLoadStage;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

/**
 * CDW 적재 파이프라인을 비동기적으로 실행한다.
 *
 * <p>검증과 저장 단계를 분리하여 CPU/I/O 부하를 분산 처리하고, 각 단계 완료 시 Kafka로 이벤트를 게시한다.
 */
@Service
public class CdwLoadPipelineService {

  private static final Logger log = LoggerFactory.getLogger(CdwLoadPipelineService.class);

  private final KafkaTemplate<String, byte[]> kafkaTemplate;
  private final AvroMessageConverter avroMessageConverter;
  private final CdwLoaderTopicProperties topicProperties;
  private final Executor cdwLoaderIoExecutor;
  private final Executor cdwLoaderCpuExecutor;

  public CdwLoadPipelineService(
      KafkaTemplate<String, byte[]> kafkaTemplate,
      AvroMessageConverter avroMessageConverter,
      CdwLoaderTopicProperties topicProperties,
      Executor cdwLoaderIoExecutor,
      Executor cdwLoaderCpuExecutor) {
    this.kafkaTemplate = kafkaTemplate;
    this.avroMessageConverter = avroMessageConverter;
    this.topicProperties = topicProperties;
    this.cdwLoaderIoExecutor = cdwLoaderIoExecutor;
    this.cdwLoaderCpuExecutor = cdwLoaderCpuExecutor;
  }

  /**
   * 배치 적재 파이프라인을 실행한다.
   *
   * @param request 적재 요청 파라미터
   * @return 전체 파이프라인 완료를 나타내는 CompletableFuture
   */
  public CompletableFuture<Void> startPipeline(CdwBatchRequest request) {
    Assert.hasText(topicProperties.getCdwLoadEvents(), "CDW 적재 이벤트 토픽이 설정되어야 합니다.");
    PipelineContext context = PipelineContext.from(request);

    return publishStage(context, CdwLoadStage.RECEIVED, context.recordCount, null, null)
        .thenCompose(ignored -> validateAsync(context))
        .thenCompose(this::persistAsync)
        .thenCompose(
            persistedContext ->
                publishStage(
                    persistedContext,
                    CdwLoadStage.PERSISTED,
                    persistedContext.recordCount,
                    null,
                    null))
        .exceptionallyCompose(throwable -> onFailure(context, throwable));
  }

  private CompletableFuture<PipelineContext> validateAsync(PipelineContext context) {
    return CompletableFuture.supplyAsync(
            () -> {
              log.info("CDW 배치 유효성 검사를 시작합니다. tenantId={}, batchId={}", context.tenantId, context.batchId);
              if (context.recordCount <= 0) {
                throw new IllegalArgumentException("recordCount는 0보다 커야 합니다.");
              }
              if (context.recordCount > 5_000_000) {
                throw new IllegalArgumentException("recordCount가 최대 허용치(5,000,000)를 초과했습니다.");
              }
              return context;
            },
            cdwLoaderCpuExecutor)
        .thenCompose(
            validContext ->
                publishStage(
                        validContext, CdwLoadStage.VALIDATED, validContext.recordCount, null, null)
                    .thenApply(ignored -> validContext));
  }

  private CompletableFuture<PipelineContext> persistAsync(PipelineContext context) {
    return CompletableFuture.supplyAsync(
        () -> {
          log.info("CDW 배치를 영속화합니다. tenantId={}, batchId={}", context.tenantId, context.batchId);
          // 실제 구현에서는 데이터베이스/오브젝트 스토리지에 저장한다.
          simulateIoLatency();
          return context;
        },
        cdwLoaderIoExecutor);
  }

  private void simulateIoLatency() {
    try {
      Thread.sleep(50);
    } catch (InterruptedException interruptedException) {
      Thread.currentThread().interrupt();
    }
  }

  private CompletableFuture<Void> publishStage(
      PipelineContext context,
      CdwLoadStage stage,
      long recordCount,
      String errorCode,
      String errorMessage) {
    CdwLoadEvent event =
        CdwLoadEvent.newBuilder()
            .setEventId(context.eventId)
            .setOccurredAt(Instant.now())
            .setTenantId(context.tenantId)
            .setBatchId(context.batchId)
            .setStage(stage)
            .setRecordCount(recordCount)
            .setSourceSystem(context.sourceSystem)
            .setErrorCode(errorCode)
            .setErrorMessage(errorMessage)
            .build();

    avroMessageConverter.validateRecord(event);
    byte[] payload = avroMessageConverter.serialize(event);
    String topic = topicProperties.getCdwLoadEvents();
    String key = context.key();
    log.info("CDW 적재 이벤트를 게시합니다. topic={}, key={}, stage={}", topic, key, stage);
    return kafkaTemplate
        .send(topic, key, payload)
        .thenAccept(
            sendResult -> {
              if (sendResult.getRecordMetadata() != null) {
                log.debug("CDW 적재 이벤트 게시 완료. topic={}, partition={}, offset={}", sendResult.getRecordMetadata().topic(), sendResult.getRecordMetadata().partition(), sendResult.getRecordMetadata().offset());
              }
            }
        );
  }

  private CompletableFuture<Void> onFailure(PipelineContext context, Throwable throwable) {
    log.error("CDW 적재 파이프라인 처리 중 예외가 발생했습니다. tenantId={}, batchId={}", context.tenantId, context.batchId, throwable);
    return publishStage(context, CdwLoadStage.FAILED, context.recordCount, "CDW-PIPELINE-ERROR", throwable.getMessage());
  }

  /** 파이프라인 실행에 필요한 공통 컨텍스트를 보관한다. */
  private static final class PipelineContext {

    private final String tenantId;
    private final String batchId;
    private final String sourceSystem;
    private final long recordCount;
    private final String eventId;

    private PipelineContext(
        String tenantId, String batchId, String sourceSystem, long recordCount, String eventId) {
      this.tenantId = Objects.requireNonNull(tenantId, "tenantId는 null일 수 없습니다.");
      this.batchId = Objects.requireNonNull(batchId, "batchId는 null일 수 없습니다.");
      this.sourceSystem = Objects.requireNonNull(sourceSystem, "sourceSystem은 null일 수 없습니다.");
      this.recordCount = recordCount;
      this.eventId = Objects.requireNonNull(eventId, "eventId는 null일 수 없습니다.");
    }

    private static PipelineContext from(CdwBatchRequest request) {
      return new PipelineContext(
          request.tenantId(),
          request.batchId(),
          request.sourceSystem(),
          request.recordCount(),
          UUID.randomUUID().toString());
    }

    private String key() {
      return tenantId + ":" + batchId;
    }
  }
}
