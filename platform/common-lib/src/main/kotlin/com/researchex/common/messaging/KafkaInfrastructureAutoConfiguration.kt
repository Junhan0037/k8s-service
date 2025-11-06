package com.researchex.common.messaging

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.config.KafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.listener.CommonErrorHandler
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.ExponentialBackOff
import java.time.Duration

/**
 * KafkaTemplate 및 ListenerContainerFactory 등을 공통으로 구성한다.
 * 각 서비스는 {@code spring.kafka.*} 속성만으로 기본 인프라 구성을 재사용할 수 있다.
 */
@AutoConfiguration
class KafkaInfrastructureAutoConfiguration {

    /** String/byte[] 기반 프로듀서 팩토리를 제공한다. */
    @Bean
    @ConditionalOnMissingBean(name = ["byteArrayProducerFactory"])
    fun byteArrayProducerFactory(kafkaProperties: KafkaProperties): ProducerFactory<String, ByteArray> {
        val props = kafkaProperties.buildProducerProperties(null)
        props.putIfAbsent(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
        props.putIfAbsent(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer::class.java)
        props.putIfAbsent(ProducerConfig.ACKS_CONFIG, "all")
        props.putIfAbsent(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true)
        return DefaultKafkaProducerFactory(props)
    }

    /** Avro 바이너리 메시지를 전송하기 위한 KafkaTemplate 을 등록한다. */
    @Bean
    @ConditionalOnMissingBean
    fun kafkaTemplate(byteArrayProducerFactory: ProducerFactory<String, ByteArray>): KafkaTemplate<String, ByteArray> {
        val kafkaTemplate = KafkaTemplate(byteArrayProducerFactory)
        kafkaTemplate.setObservationEnabled(true)
        return kafkaTemplate
    }

    /** String/byte[] 기반 컨슈머 팩토리를 구성한다. */
    @Bean
    @ConditionalOnMissingBean(name = ["byteArrayConsumerFactory"])
    fun byteArrayConsumerFactory(kafkaProperties: KafkaProperties): ConsumerFactory<String, ByteArray> {
        val props = kafkaProperties.buildConsumerProperties(null)
        props.putIfAbsent(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
        props.putIfAbsent(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer::class.java)
        props.putIfAbsent(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false)
        return DefaultKafkaConsumerFactory(props)
    }

    /** 멱등성/재처리를 고려한 공통 에러 핸들러를 구성한다. */
    @Bean
    @ConditionalOnMissingBean
    fun kafkaErrorHandler(): CommonErrorHandler {
        val backOff = ExponentialBackOff().apply {
            initialInterval = Duration.ofSeconds(1).toMillis()
            multiplier = 2.0
            maxElapsedTime = Duration.ofMinutes(5).toMillis()
        }
        return DefaultErrorHandler(backOff).apply {
            addNotRetryableExceptions(AvroSerializationException::class.java)
        }
    }

    /** 수동 커밋 전략을 사용하는 Kafka Listener Container Factory 를 등록한다. */
    @Bean
    @ConditionalOnMissingBean
    fun kafkaListenerContainerFactory(
        byteArrayConsumerFactory: ConsumerFactory<String, ByteArray>,
        kafkaErrorHandler: CommonErrorHandler,
        kafkaProperties: KafkaProperties
    ): KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<String, ByteArray>> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, ByteArray>()
        factory.consumerFactory = byteArrayConsumerFactory
        factory.setCommonErrorHandler(kafkaErrorHandler)
        factory.setConcurrency(resolveConcurrency(kafkaProperties))
        val containerProperties: ContainerProperties = factory.containerProperties
        containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
        containerProperties.isObservationEnabled = true
        containerProperties.isMicrometerEnabled = true
        return factory
    }

    /** spring.kafka.listener.concurrency가 1 미만이거나 비어 있으면 1로 보정한다. */
    private fun resolveConcurrency(kafkaProperties: KafkaProperties): Int {
        val configuredConcurrency = kafkaProperties.listener.concurrency
        return if (configuredConcurrency == null || configuredConcurrency < 1) 1 else configuredConcurrency
    }

    /**
     * Avro <-> byte[] 변환을 담당하는 커스텀 도우미 Bean.
     * 프로듀서/컨슈머에서 도메인 객체 ↔ Avro 바이너리 변환을 일관되게 처리하는 용도다.
     */
    @Bean
    @ConditionalOnMissingBean
    fun avroMessageConverter(): AvroMessageConverter {
        return AvroMessageConverter()
    }
}
