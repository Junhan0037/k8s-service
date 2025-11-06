package com.researchex.deid.messaging

import com.researchex.common.messaging.AvroMessageConverter
import com.researchex.contract.cdw.CdwLoadEvent
import com.researchex.deid.application.DeidPipelineService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.time.Duration

/** CDW 적재 이벤트를 수신해 가명화 파이프라인을 기동한다. */
@Component
class CdwLoadEventListener(
    private val avroMessageConverter: AvroMessageConverter,
    private val deidPipelineService: DeidPipelineService,
) {

    @KafkaListener(
        topics = ["\${app.messaging.topics.cdw-load-events}"],
        groupId = "\${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory",
    )
    fun onMessage(consumerRecord: ConsumerRecord<String, ByteArray>, acknowledgment: Acknowledgment) {
        try {
            val payload = consumerRecord.value()
            if (payload == null) {
                log.warn("Kafka 메시지 페이로드가 비어 있어 건너뜁니다. key={}", consumerRecord.key())
                acknowledgment.acknowledge()
                return
            }

            val cdwLoadEvent = avroMessageConverter.deserialize(payload, CdwLoadEvent::class.java)
            avroMessageConverter.validateRecord(cdwLoadEvent)
            val pipelineFuture = deidPipelineService.handleCdwLoadEvent(cdwLoadEvent)
            pipelineFuture.whenComplete { _, throwable ->
                if (throwable != null) {
                    log.error(
                        "가명화 파이프라인 처리 중 예외가 발생했습니다. key={}",
                        consumerRecord.key(),
                        throwable,
                    )
                    acknowledgment.nack(Duration.ofSeconds(1))
                } else {
                    acknowledgment.acknowledge()
                }
            }
        } catch (exception: Exception) {
            log.error("CDW 이벤트 수신 처리에 실패했습니다. key={}", consumerRecord.key(), exception)
            acknowledgment.nack(Duration.ofSeconds(1))
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(CdwLoadEventListener::class.java)
    }
}
