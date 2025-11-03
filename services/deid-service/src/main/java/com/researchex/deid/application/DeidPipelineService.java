package com.researchex.deid.application;

import com.researchex.common.messaging.AvroMessageConverter;
import com.researchex.contract.cdw.CdwLoadEvent;
import com.researchex.contract.cdw.CdwLoadStage;
import com.researchex.contract.deid.DeidJobEvent;
import com.researchex.contract.deid.DeidStage;
import com.researchex.deid.config.DeidServiceTopicProperties;
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

/** CDW 로딩 이벤트를 입력으로 받아 가명화 파이프라인을 비동기적으로 실행한다. */
@Service
public class DeidPipelineService {

  private static final Logger log = LoggerFactory.getLogger(DeidPipelineService.class);

  private final KafkaTemplate<String, byte[]> kafkaTemplate;
  private final AvroMessageConverter avroMessageConverter;
  private final DeidServiceTopicProperties topicProperties;
  private final Executor deidIoExecutor;
  private final Executor deidCpuExecutor;

  public DeidPipelineService(
      KafkaTemplate<String, byte[]> kafkaTemplate,
      AvroMessageConverter avroMessageConverter,
      DeidServiceTopicProperties topicProperties,
      Executor deidIoExecutor,
      Executor deidCpuExecutor) {
    this.kafkaTemplate = kafkaTemplate;
    this.avroMessageConverter = avroMessageConverter;
    this.topicProperties = topicProperties;
    this.deidIoExecutor = deidIoExecutor;
    this.deidCpuExecutor = deidCpuExecutor;
  }

  /**
   * CDW 로딩 이벤트를 기반으로 가명화 파이프라인을 수행한다.
   *
   * @param cdwLoadEvent Kafka에서 수신한 CDW 로딩 이벤트
   * @return 파이프라인 완료 시점을 나타내는 {@link CompletableFuture}
   */
  public CompletableFuture<Void> handleCdwLoadEvent(CdwLoadEvent cdwLoadEvent) {
    if (cdwLoadEvent.getStage() != CdwLoadStage.PERSISTED) {
      log.debug("CDW 이벤트가 PERSISTED 단계가 아니므로 가명화 파이프라인을 건너뜁니다. stage={}", cdwLoadEvent.getStage());
      return CompletableFuture.completedFuture(null);
    }

    Assert.hasText(topicProperties.getDeidJobs(), "deid.jobs 토픽이 설정되어야 합니다.");
    Assert.hasText(topicProperties.getCdwLoadEvents(), "cdw.load.events 토픽이 설정되어야 합니다.");

    DeidJobContext context = DeidJobContext.from(cdwLoadEvent);
    return publishStage(context, DeidStage.REQUESTED, context.rawPayloadLocation, null)
        .thenCompose(ignored -> validateAsync(context))
        .thenCompose(validContext -> publishStage(validContext, DeidStage.RUNNING, validContext.rawPayloadLocation, null))
        .thenCompose(ignored -> maskAsync(context))
        .thenCompose(maskedContext -> publishStage(maskedContext, DeidStage.COMPLETED, maskedContext.outputPayloadLocation, null))
        .exceptionallyCompose(
            throwable -> {
              log.error("가명화 파이프라인 처리 중 오류가 발생했습니다. tenantId={}, jobId={}", context.tenantId, context.jobId, throwable);
              return publishStage(
                  context, DeidStage.FAILED, context.rawPayloadLocation, throwable.getMessage());
            });
  }

  private CompletableFuture<DeidJobContext> validateAsync(DeidJobContext context) {
    return CompletableFuture.supplyAsync(
        () -> {
          log.info("가명화 입력 검증을 수행합니다. tenantId={}, jobId={}", context.tenantId, context.jobId);
          if (context.recordCount <= 0) {
            throw new IllegalArgumentException("가명화 입력 레코드 수가 0 이하입니다.");
          }
          if (context.recordCount > 10_000_000) {
            throw new IllegalArgumentException("가명화 입력 레코드 수가 허용치를 초과했습니다.");
          }
          return context;
        },
        deidCpuExecutor);
  }

  private CompletableFuture<DeidJobContext> maskAsync(DeidJobContext context) {
    return CompletableFuture.supplyAsync(
        () -> {
          log.info("가명화 마스킹 파이프라인을 실행합니다. tenantId={}, jobId={}", context.tenantId, context.jobId);
          simulateIoLatency();
          return context;
        },
        deidIoExecutor);
  }

  private void simulateIoLatency() {
    try {
      Thread.sleep(100);
    } catch (InterruptedException interruptedException) {
      Thread.currentThread().interrupt();
    }
  }

  private CompletableFuture<Void> publishStage(
      DeidJobContext context, DeidStage stage, String payloadLocation, String errorMessage) {
    DeidJobEvent event =
        DeidJobEvent.newBuilder()
            .setEventId(context.eventId)
            .setOccurredAt(Instant.now())
            .setTenantId(context.tenantId)
            .setJobId(context.jobId)
            .setStage(stage)
            .setPayloadLocation(payloadLocation)
            .setErrorCode(errorMessage == null ? null : "DEID-PIPELINE-ERROR")
            .setErrorMessage(errorMessage)
            .build();

    avroMessageConverter.validateRecord(event);
    byte[] payload = avroMessageConverter.serialize(event);
    String topic = topicProperties.getDeidJobs();
    String key = context.key();
    log.info("가명화 파이프라인 이벤트를 게시합니다. topic={}, key={}, stage={}", topic, key, stage);
    return kafkaTemplate
        .send(topic, key, payload)
        .thenAccept(
            sendResult -> {
              if (sendResult.getRecordMetadata() != null) {
                log.debug(
                    "가명화 이벤트 게시 완료. topic={}, partition={}, offset={}",
                    sendResult.getRecordMetadata().topic(),
                    sendResult.getRecordMetadata().partition(),
                    sendResult.getRecordMetadata().offset());
              }
            });
  }

  private static final class DeidJobContext {

    private final String tenantId;
    private final String jobId;
    private final String batchId;
    private final String rawPayloadLocation;
    private final String outputPayloadLocation;
    private final long recordCount;
    private final String eventId;

    private DeidJobContext(
        String tenantId,
        String jobId,
        String batchId,
        String rawPayloadLocation,
        String outputPayloadLocation,
        long recordCount,
        String eventId) {
      this.tenantId = Objects.requireNonNull(tenantId, "tenantId는 null일 수 없습니다.");
      this.jobId = Objects.requireNonNull(jobId, "jobId는 null일 수 없습니다.");
      this.batchId = Objects.requireNonNull(batchId, "batchId는 null일 수 없습니다.");
      this.rawPayloadLocation = Objects.requireNonNull(rawPayloadLocation, "rawPayloadLocation은 null일 수 없습니다.");
      this.outputPayloadLocation = Objects.requireNonNull(outputPayloadLocation, "outputPayloadLocation은 null일 수 없습니다.");
      this.recordCount = recordCount;
      this.eventId = Objects.requireNonNull(eventId, "eventId는 null일 수 없습니다.");
    }

    private static DeidJobContext from(CdwLoadEvent event) {
      String tenantId = event.getTenantId();
      String batchId = event.getBatchId();
      String rawLocation = "s3://raw/" + tenantId + "/" + batchId;
      String jobId = UUID.randomUUID() + "-" + batchId;
      String outputLocation = "s3://deid/" + tenantId + "/" + jobId;
      long recordCount = event.getRecordCount() == null ? 0 : event.getRecordCount();
      return new DeidJobContext(
          tenantId,
          jobId,
          batchId,
          rawLocation,
          outputLocation,
          recordCount,
              event.getEventId());
    }

    private String key() {
      return tenantId + ":" + jobId;
    }
  }
}
