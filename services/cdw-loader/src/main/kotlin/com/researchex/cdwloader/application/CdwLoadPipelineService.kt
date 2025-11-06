package com.researchex.cdwloader.application

import com.researchex.cdwloader.api.CdwBatchRequest
import com.researchex.cdwloader.config.CdwLoaderTopicProperties
import com.researchex.common.messaging.AvroMessageConverter
import com.researchex.contract.cdw.CdwLoadEvent
import com.researchex.contract.cdw.CdwLoadStage
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

/**
 * CDW 적재 파이프라인을 비동기적으로 실행한다.
 * 검증과 저장 단계를 분리하여 CPU/I/O 부하를 분산 처리하고, 각 단계 완료 시 Kafka로 이벤트를 게시한다.
 */
@Service
class CdwLoadPipelineService(
    private val kafkaTemplate: KafkaTemplate<String, ByteArray>,
    private val avroMessageConverter: AvroMessageConverter,
    private val topicProperties: CdwLoaderTopicProperties,
    private val cdwLoaderIoExecutor: Executor,
    private val cdwLoaderCpuExecutor: Executor,
) {

    /**
     * 배치 적재 파이프라인을 실행한다.
     */
    fun startPipeline(request: CdwBatchRequest): CompletableFuture<Void> {
        val topic = topicProperties.cdwLoadEvents
            ?: throw IllegalArgumentException("CDW 적재 이벤트 토픽이 설정되어야 합니다.")
        require(topic.isNotBlank()) { "CDW 적재 이벤트 토픽이 설정되어야 합니다." }

        val context = PipelineContext.from(request)

        return publishStage(context, CdwLoadStage.RECEIVED, context.recordCount, null, null)
            .thenCompose { validateAsync(context) }
            .thenCompose { persistAsync(it) }
            .thenCompose { persistedContext ->
                publishStage(
                    persistedContext,
                    CdwLoadStage.PERSISTED,
                    persistedContext.recordCount,
                    null,
                    null,
                )
            }
            .exceptionallyCompose { throwable -> onFailure(context, throwable) }
    }

    private fun validateAsync(context: PipelineContext): CompletableFuture<PipelineContext> {
        return CompletableFuture.supplyAsync(
            {
                log.info(
                    "CDW 배치 유효성 검사를 시작합니다. tenantId={}, batchId={}",
                    context.tenantId,
                    context.batchId,
                )
                require(context.recordCount > 0) { "recordCount는 0보다 커야 합니다." }
                require(context.recordCount <= 5_000_000) { "recordCount가 최대 허용치(5,000,000)를 초과했습니다." }
                context
            },
            cdwLoaderCpuExecutor,
        ).thenCompose { validContext ->
            publishStage(validContext, CdwLoadStage.VALIDATED, validContext.recordCount, null, null)
                .thenApply { validContext }
        }
    }

    private fun persistAsync(context: PipelineContext): CompletableFuture<PipelineContext> {
        return CompletableFuture.supplyAsync(
            {
                log.info(
                    "CDW 배치를 영속화합니다. tenantId={}, batchId={}",
                    context.tenantId,
                    context.batchId,
                )
                simulateIoLatency()
                context
            },
            cdwLoaderIoExecutor,
        )
    }

    private fun simulateIoLatency() {
        try {
            Thread.sleep(50)
        } catch (interruptedException: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun publishStage(
        context: PipelineContext,
        stage: CdwLoadStage,
        recordCount: Long,
        errorCode: String?,
        errorMessage: String?,
    ): CompletableFuture<Void> {
        val event = CdwLoadEvent.newBuilder()
            .setEventId(context.eventId)
            .setOccurredAt(Instant.now())
            .setTenantId(context.tenantId)
            .setBatchId(context.batchId)
            .setStage(stage)
            .setRecordCount(recordCount)
            .setSourceSystem(context.sourceSystem)
            .setErrorCode(errorCode)
            .setErrorMessage(errorMessage)
            .build()

        avroMessageConverter.validateRecord(event)
        val payload = avroMessageConverter.serialize(event)
        val topic = requireNotNull(topicProperties.cdwLoadEvents) { "CDW 적재 이벤트 토픽이 설정되어야 합니다." }
        val key = context.key()
        log.info("CDW 적재 이벤트를 게시합니다. topic={}, key={}, stage={}", topic, key, stage)
        return kafkaTemplate.send(topic, key, payload)
            .thenAccept { sendResult ->
                val metadata = sendResult.recordMetadata
                if (metadata != null) {
                    log.debug(
                        "CDW 적재 이벤트 게시 완료. topic={}, partition={}, offset={}",
                        metadata.topic(),
                        metadata.partition(),
                        metadata.offset(),
                    )
                }
            }
    }

    private fun onFailure(context: PipelineContext, throwable: Throwable?): CompletableFuture<Void> {
        log.error(
            "CDW 적재 파이프라인 처리 중 예외가 발생했습니다. tenantId={}, batchId={}",
            context.tenantId,
            context.batchId,
            throwable,
        )
        return publishStage(
            context,
            CdwLoadStage.FAILED,
            context.recordCount,
            "CDW-PIPELINE-ERROR",
            throwable?.message,
        )
    }

    private data class PipelineContext(
        val tenantId: String,
        val batchId: String,
        val sourceSystem: String,
        val recordCount: Long,
        val eventId: String,
    ) {
        fun key(): String = "$tenantId:$batchId"

        companion object {
            fun from(request: CdwBatchRequest): PipelineContext {
                return PipelineContext(
                    tenantId = request.tenantId,
                    batchId = request.batchId,
                    sourceSystem = request.sourceSystem,
                    recordCount = request.recordCount,
                    eventId = UUID.randomUUID().toString(),
                )
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(CdwLoadPipelineService::class.java)
    }
}
