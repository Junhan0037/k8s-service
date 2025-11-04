package com.researchex.common.messaging;

import java.time.Duration;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * KafkaTemplate 및 ListenerContainerFactory 등을 공통으로 구성한다.
 * 각 서비스는 {@code spring.kafka.*} 속성만으로 기본 인프라 구성을 재사용할 수 있다.
 */
@AutoConfiguration
public class KafkaInfrastructureAutoConfiguration {

  /** String/byte[] 기반 프로듀서 팩토리를 제공한다. */
  @Bean
  @ConditionalOnMissingBean(name = "byteArrayProducerFactory")
  public ProducerFactory<String, byte[]> byteArrayProducerFactory(KafkaProperties kafkaProperties) {
    Map<String, Object> props = kafkaProperties.buildProducerProperties(null);
    props.putIfAbsent(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.putIfAbsent(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class); // Avro 바이너리 전송 전제
    props.putIfAbsent(ProducerConfig.ACKS_CONFIG, "all"); // acks=all 설정. 리더 + 팔로워 모두 쓰기 확인, 내구성 강화
    props.putIfAbsent(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true); // 재시도 시 중목 메시지 전송 방지
    return new DefaultKafkaProducerFactory<>(props);
  }

  /** Avro 바이너리 메시지를 전송하기 위한 KafkaTemplate 을 등록한다. */
  @Bean
  @ConditionalOnMissingBean
  public KafkaTemplate<String, byte[]> kafkaTemplate(
      ProducerFactory<String, byte[]> byteArrayProducerFactory) {
    KafkaTemplate<String, byte[]> kafkaTemplate = new KafkaTemplate<>(byteArrayProducerFactory);
    kafkaTemplate.setObservationEnabled(true); // Micrometer Observation 연동으로 메트릭/트레이싱 자동 수립
    return kafkaTemplate;
  }

  /** String/byte[] 기반 컨슈머 팩토리를 구성한다. */
  @Bean
  @ConditionalOnMissingBean(name = "byteArrayConsumerFactory")
  public ConsumerFactory<String, byte[]> byteArrayConsumerFactory(KafkaProperties kafkaProperties) {
    Map<String, Object> props = kafkaProperties.buildConsumerProperties(null);
    props.putIfAbsent(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.putIfAbsent(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
    props.putIfAbsent(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // 자동 커밋 끄고, 애플리케이션이 처리 성공 시점에 직접 커밋
    return new DefaultKafkaConsumerFactory<>(props);
  }

  /** 멱등성/재처리를 고려한 공통 에러 핸들러를 구성한다. */
  @Bean
  @ConditionalOnMissingBean
  public CommonErrorHandler kafkaErrorHandler() {
    ExponentialBackOff backOff = new ExponentialBackOff();
    backOff.setInitialInterval(Duration.ofSeconds(1).toMillis()); // 초기 1초
    backOff.setMultiplier(2.0); // 배수 2.0
    backOff.setMaxElapsedTime(Duration.ofMinutes(5).toMillis()); // 총 경과 최대 5분
    DefaultErrorHandler errorHandler = new DefaultErrorHandler(backOff);
    errorHandler.addNotRetryableExceptions(AvroSerializationException.class); // 재시도해도 소용없는 예외 등록
    return errorHandler;
  }

  /** 수동 커밋 전략을 사용하는 Kafka Listener Container Factory 를 등록한다. */
  @Bean
  @ConditionalOnMissingBean
  public KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<String, byte[]>>
      kafkaListenerContainerFactory(
          ConsumerFactory<String, byte[]> byteArrayConsumerFactory,
          CommonErrorHandler kafkaErrorHandler,
          KafkaProperties kafkaProperties) {
    ConcurrentKafkaListenerContainerFactory<String, byte[]> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(byteArrayConsumerFactory);
    factory.setCommonErrorHandler(kafkaErrorHandler);
    factory.setConcurrency(resolveConcurrency(kafkaProperties));
    ContainerProperties containerProperties = factory.getContainerProperties();
    containerProperties.setAckMode(ContainerProperties.AckMode.MANUAL); // 리스너 로직이 성공적으로 끝난 뒤 명시적으로 ack.acknowledge() 호출해 오프셋 커밋
    containerProperties.setObservationEnabled(true); // 리스너 측도 관측 정보 수집
    containerProperties.setMicrometerEnabled(true); // 컨슈머 랙/처리량 등 Micrometer 메트릭 수집
    return factory;
  }

  /** spring.kafka.listener.concurrency가 1 미만이거나 비어 있으면 1로 보정 */
  private int resolveConcurrency(KafkaProperties kafkaProperties) {
    Integer configuredConcurrency = kafkaProperties.getListener().getConcurrency();
    return configuredConcurrency == null || configuredConcurrency < 1 ? 1 : configuredConcurrency;
  }

  /**
   * Avro <-> byte[] 변환을 담당하는 커스텀 도우미 Bean.
   * 프로듀서/컨슈머에서 도메인 객체 ↔ Avro 바이너리 변환을 일관되게 처리하는 용도
   * */
  @Bean
  @ConditionalOnMissingBean
  public AvroMessageConverter avroMessageConverter() {
    return new AvroMessageConverter();
  }
}
