package com.researchex.cdwloader.application;

import com.researchex.cdwloader.api.CdwBatchRequest;
import com.researchex.common.messaging.AvroMessageConverter;
import com.researchex.contract.cdw.CdwLoadEvent;
import com.researchex.contract.cdw.CdwLoadStage;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kafka Testcontainer를 활용해 {@link CdwLoadPipelineService}가 Avro 이벤트를 정상 게시하는지 검증한다.
 * 생산된 페이로드를 역직렬화해 스키마 계약을 확인함으로써 17단계 테스트 전략의 컨테이너 및 계약 테스트 요구사항을 충족한다.
 */
@SpringBootTest
@Testcontainers
class CdwLoadPipelineServiceIntegrationTest {

    private static final String TOPIC_NAME = "researchex.cdw.load.test";

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @DynamicPropertySource // 테스트 실행 중 동적으로 값이 결정되는 경우, 동적으로 환경 프로퍼티를 등록
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.consumer.group-id", () -> "cdw-loader-int-test");
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("app.messaging.topics.cdw-load-events", () -> TOPIC_NAME);
    }

    @Autowired
    private CdwLoadPipelineService pipelineService;

    @Autowired
    private AvroMessageConverter avroMessageConverter;

    @Test
    void startPipelinePublishesCdwLoadEvents() throws Exception {
        try (KafkaConsumer<String, byte[]> consumer = createConsumer()) {
            consumer.subscribe(List.of(TOPIC_NAME));

            pipelineService.startPipeline(new CdwBatchRequest("tenant-x", "batch-100", "CDW", 1500)).get(10, TimeUnit.SECONDS);

            List<CdwLoadEvent> events = pollEvents(consumer, 3);

            assertThat(events).hasSizeGreaterThanOrEqualTo(3);
            List<CdwLoadStage> stages = events.stream().map(CdwLoadEvent::getStage).toList();
            assertThat(stages).contains(CdwLoadStage.RECEIVED, CdwLoadStage.VALIDATED, CdwLoadStage.PERSISTED);

            // Avro 계약 검증: 필수 필드가 누락되지 않았는지 확인한다.
            CdwLoadEvent latestEvent = events.get(events.size() - 1);
            assertThat(latestEvent.getTenantId()).isEqualTo("tenant-x");
            assertThat(latestEvent.getBatchId()).isEqualTo("batch-100");
            assertThat(latestEvent.getRecordCount()).isEqualTo(1500);
        }
    }

    private KafkaConsumer<String, byte[]> createConsumer() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "cdw-loader-int-test-consumer");
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        return new KafkaConsumer<>(properties);
    }

    private List<CdwLoadEvent> pollEvents(KafkaConsumer<String, byte[]> consumer, int expectedMinimum) {
        List<CdwLoadEvent> collected = new ArrayList<>();
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(15).toMillis();

        while (System.currentTimeMillis() < deadline && collected.size() < expectedMinimum) {
            ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, byte[]> record : records) {
                collected.add(avroMessageConverter.deserialize(record.value(), CdwLoadEvent.class));
            }
        }

        return collected;
    }
}
