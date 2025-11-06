package com.researchex.research.messaging

import com.researchex.common.messaging.AvroMessageConverter
import com.researchex.contract.deid.DeidJobEvent
import com.researchex.research.application.ResearchIndexService
import io.micrometer.observation.annotation.Observed
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.time.Duration

/** 가명화 완료 이벤트를 구독해 연구 인덱스 업데이트를 트리거한다. */
@Component
class DeidJobEventListener(
    private val avroMessageConverter: AvroMessageConverter,
    private val researchIndexService: ResearchIndexService
) {

    @KafkaListener(
        topics = ["\${app.messaging.topics.deid-jobs}"],
        groupId = "\${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Observed(
        name = "researchex.research.deid.consumer",
        contextualName = "deid-job-consumer",
        lowCardinalityKeyValues = ["topic", "deid.jobs"]
    )
    fun onMessage(consumerRecord: ConsumerRecord<String, ByteArray>, acknowledgment: Acknowledgment) {
        try {
            val payload = consumerRecord.value()
            if (payload == null) {
                log.warn("Kafka 메시지 페이로드가 비어 있어 건너뜁니다. key={}", consumerRecord.key())
                acknowledgment.acknowledge()
                return
            }
            val deidJobEvent = avroMessageConverter.deserialize(payload, DeidJobEvent::class.java)
            avroMessageConverter.validateRecord(deidJobEvent)
            val completionFuture = researchIndexService.handleDeidJobEvent(deidJobEvent).toFuture()
            completionFuture.whenComplete { _, throwable ->
                if (throwable != null) {
                    log.error(
                        "연구 인덱싱 처리 중 예외가 발생했습니다. key={}",
                        consumerRecord.key(),
                        throwable
                    )
                    acknowledgment.nack(Duration.ofSeconds(1))
                } else {
                    acknowledgment.acknowledge()
                }
            }
        } catch (exception: Exception) {
            log.error("Deid Job 이벤트 수신 처리에 실패했습니다. key={}", consumerRecord.key(), exception)
            acknowledgment.nack(Duration.ofSeconds(1))
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(DeidJobEventListener::class.java)
    }
}
