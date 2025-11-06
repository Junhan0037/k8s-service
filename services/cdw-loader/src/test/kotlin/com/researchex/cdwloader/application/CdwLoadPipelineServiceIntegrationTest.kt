package com.researchex.cdwloader.application

import com.researchex.cdwloader.api.CdwBatchRequest
import com.researchex.common.messaging.AvroMessageConverter
import com.researchex.contract.cdw.CdwLoadEvent
import com.researchex.contract.cdw.CdwLoadStage
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.util.Properties
import java.util.concurrent.TimeUnit

/**
 * Kafka Testcontainer를 활용해 [CdwLoadPipelineService]가 Avro 이벤트를 정상 게시하는지 검증한다.
 * 생산된 페이로드를 역직렬화해 스키마 계약을 확인함으로써 컨테이너 및 계약 테스트 요구사항을 충족한다.
 */
@SpringBootTest
@Testcontainers
class CdwLoadPipelineServiceIntegrationTest @Autowired constructor(
    private val pipelineService: CdwLoadPipelineService,
    private val avroMessageConverter: AvroMessageConverter,
) {

    @Test
    fun startPipelinePublishesCdwLoadEvents() {
        createConsumer().use { consumer ->
            consumer.subscribe(listOf(TOPIC_NAME))

            pipelineService
                .startPipeline(CdwBatchRequest("tenant-x", "batch-100", "CDW", 1500))
                .get(10, TimeUnit.SECONDS)

            val events = pollEvents(consumer, 3)

            assertThat(events).hasSizeGreaterThanOrEqualTo(3)
            val stages = events.map { it.stage }
            assertThat(stages).contains(CdwLoadStage.RECEIVED, CdwLoadStage.VALIDATED, CdwLoadStage.PERSISTED)

            val latestEvent = events.last()
            assertThat(latestEvent.tenantId).isEqualTo("tenant-x")
            assertThat(latestEvent.batchId).isEqualTo("batch-100")
            assertThat(latestEvent.recordCount).isEqualTo(1500)
        }
    }

    private fun createConsumer(): KafkaConsumer<String, ByteArray> {
        val properties = Properties().apply {
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.bootstrapServers)
            put(ConsumerConfig.GROUP_ID_CONFIG, "cdw-loader-int-test-consumer")
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true)
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer::class.java)
        }
        return KafkaConsumer(properties)
    }

    private fun pollEvents(
        consumer: KafkaConsumer<String, ByteArray>,
        expectedMinimum: Int,
    ): List<CdwLoadEvent> {
        val collected = mutableListOf<CdwLoadEvent>()
        val deadline = System.currentTimeMillis() + Duration.ofSeconds(15).toMillis()

        while (System.currentTimeMillis() < deadline && collected.size < expectedMinimum) {
            val records: ConsumerRecords<String, ByteArray> = consumer.poll(Duration.ofMillis(500))
            for (record: ConsumerRecord<String, ByteArray> in records) {
                collected += avroMessageConverter.deserialize(record.value(), CdwLoadEvent::class.java)
            }
        }

        return collected
    }

    companion object {
        private const val TOPIC_NAME = "researchex.cdw.load.test"

        @Container
        @JvmStatic
        val kafka: KafkaContainer = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"))

        @JvmStatic
        @DynamicPropertySource
        fun overrideProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.kafka.bootstrap-servers") { kafka.bootstrapServers }
            registry.add("spring.kafka.consumer.group-id") { "cdw-loader-int-test" }
            registry.add("spring.kafka.consumer.auto-offset-reset") { "earliest" }
            registry.add("app.messaging.topics.cdw-load-events") { TOPIC_NAME }
        }
    }
}
