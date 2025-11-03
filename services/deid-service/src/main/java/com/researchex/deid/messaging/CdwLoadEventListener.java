package com.researchex.deid.messaging;

import com.researchex.common.messaging.AvroMessageConverter;
import com.researchex.contract.cdw.CdwLoadEvent;
import com.researchex.deid.application.DeidPipelineService;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/** CDW 적재 이벤트를 수신해 가명화 파이프라인을 기동한다. */
@Component
public class CdwLoadEventListener {

  private static final Logger log = LoggerFactory.getLogger(CdwLoadEventListener.class);

  private final AvroMessageConverter avroMessageConverter;
  private final DeidPipelineService deidPipelineService;

  public CdwLoadEventListener(
      AvroMessageConverter avroMessageConverter, DeidPipelineService deidPipelineService) {
    this.avroMessageConverter = avroMessageConverter;
    this.deidPipelineService = deidPipelineService;
  }

  @KafkaListener(
      topics = "${app.messaging.topics.cdw-load-events}",
      groupId = "${spring.kafka.consumer.group-id}",
      containerFactory = "kafkaListenerContainerFactory")
  public void onMessage(
      ConsumerRecord<String, byte[]> consumerRecord, Acknowledgment acknowledgment) {
    try {
      if (consumerRecord.value() == null) {
        log.warn("Kafka 메시지 페이로드가 비어 있어 건너뜁니다. key={}", consumerRecord.key());
        acknowledgment.acknowledge();
        return;
      }
      CdwLoadEvent cdwLoadEvent = avroMessageConverter.deserialize(consumerRecord.value(), CdwLoadEvent.class);
      avroMessageConverter.validateRecord(cdwLoadEvent);
      CompletableFuture<Void> pipelineFuture = deidPipelineService.handleCdwLoadEvent(cdwLoadEvent);
      pipelineFuture.whenComplete(
          (ignored, throwable) -> {
            if (throwable != null) {
              log.error("가명화 파이프라인 처리 중 예외가 발생했습니다. key={}", consumerRecord.key(), throwable);
              acknowledgment.nack(Duration.ofSeconds(1));
            } else {
              acknowledgment.acknowledge();
            }
          });
    } catch (Exception exception) {
      log.error("CDW 이벤트 수신 처리에 실패했습니다. key={}", consumerRecord.key(), exception);
      acknowledgment.nack(Duration.ofSeconds(1));
    }
  }
}
