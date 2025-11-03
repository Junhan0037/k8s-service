package com.researchex.research.messaging;

import com.researchex.common.messaging.AvroMessageConverter;
import com.researchex.contract.deid.DeidJobEvent;
import com.researchex.research.application.ResearchIndexService;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** 가명화 완료 이벤트를 구독해 연구 인덱스 업데이트를 트리거한다. */
@Component
public class DeidJobEventListener {

  private static final Logger log = LoggerFactory.getLogger(DeidJobEventListener.class);

  private final AvroMessageConverter avroMessageConverter;
  private final ResearchIndexService researchIndexService;

  public DeidJobEventListener(
      AvroMessageConverter avroMessageConverter, ResearchIndexService researchIndexService) {
    this.avroMessageConverter = avroMessageConverter;
    this.researchIndexService = researchIndexService;
  }

  @KafkaListener(
      topics = "${app.messaging.topics.deid-jobs}",
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
      DeidJobEvent deidJobEvent =
          avroMessageConverter.deserialize(consumerRecord.value(), DeidJobEvent.class);
      avroMessageConverter.validateRecord(deidJobEvent);
      Mono<Void> indexingMono = researchIndexService.handleDeidJobEvent(deidJobEvent);
      CompletableFuture<Void> completionFuture = indexingMono.toFuture();
      completionFuture.whenComplete(
          (ignored, throwable) -> {
            if (throwable != null) {
              log.error("연구 인덱싱 처리 중 예외가 발생했습니다. key={}", consumerRecord.key(), throwable);
              acknowledgment.nack(Duration.ofSeconds(1));
            } else {
              acknowledgment.acknowledge();
            }
          });
    } catch (Exception exception) {
      log.error("Deid Job 이벤트 수신 처리에 실패했습니다. key={}", consumerRecord.key(), exception);
      acknowledgment.nack(Duration.ofSeconds(1));
    }
  }
}
