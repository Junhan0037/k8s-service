package com.researchex.deid.application

import com.researchex.common.messaging.AvroMessageConverter
import com.researchex.contract.cdw.CdwLoadEvent
import com.researchex.contract.cdw.CdwLoadStage
import com.researchex.contract.deid.DeidJobEvent
import com.researchex.contract.deid.DeidStage
import com.researchex.deid.config.DeidServiceTopicProperties
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.Objects
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

/** CDW 로딩 이벤트를 입력으로 받아 가명화 파이프라인을 비동기적으로 실행한다. */
@Service
class DeidPipelineService(
    private val kafkaTemplate: KafkaTemplate<String, ByteArray>,
    private val avroMessageConverter: AvroMessageConverter,
    private val topicProperties: DeidServiceTopicProperties,
    private val deidIoExecutor: Executor,
    private val deidCpuExecutor: Executor,
) {

    fun handleCdwLoadEvent(cdwLoadEvent: CdwLoadEvent): CompletableFuture<Void> {
        if (cdwLoadEvent.stage != CdwLoadStage.PERSISTED) {
            log.debug(
                "CDW 이벤트가 PERSISTED 단계가 아니므로 가명화 파이프라인을 건너뜁니다. stage={}",
                cdwLoadEvent.stage,
            )
            return CompletableFuture.completedFuture(null)
        }

        val deidTopic = topicProperties.deidJobs
            ?: throw IllegalArgumentException("deid.jobs 토픽이 설정되어야 합니다.")
        val cdwTopic = topicProperties.cdwLoadEvents
            ?: throw IllegalArgumentException("cdw.load.events 토픽이 설정되어야 합니다.")
        // cdwTopic은 현재 직접 사용하진 않지만, 존재 여부를 사전 검증한다.
        require(cdwTopic.isNotBlank()) { "cdw.load.events 토픽이 설정되어야 합니다." }

        val context = DeidJobContext.from(cdwLoadEvent, deidTopic)
        return publishStage(context, DeidStage.REQUESTED, context.rawPayloadLocation, null)
            .thenCompose { validateAsync(context) }
            .thenCompose { validContext ->
                publishStage(validContext, DeidStage.RUNNING, validContext.rawPayloadLocation, null)
            }
            .thenCompose { maskAsync(context) }
            .thenCompose { maskedContext ->
                publishStage(
                    maskedContext,
                    DeidStage.COMPLETED,
                    maskedContext.outputPayloadLocation,
                    null,
                )
            }
            .exceptionallyCompose { throwable ->
                log.error(
                    "가명화 파이프라인 처리 중 오류가 발생했습니다. tenantId={}, jobId={}",
                    context.tenantId,
                    context.jobId,
                    throwable,
                )
                publishStage(
                    context,
                    DeidStage.FAILED,
                    context.rawPayloadLocation,
                    throwable?.message,
                )
            }
    }

    private fun validateAsync(context: DeidJobContext): CompletableFuture<DeidJobContext> {
        return CompletableFuture.supplyAsync(
            {
                log.info(
                    "가명화 입력 검증을 수행합니다. tenantId={}, jobId={}",
                    context.tenantId,
                    context.jobId,
                )
                require(context.recordCount > 0) { "가명화 입력 레코드 수가 0 이하입니다." }
                require(context.recordCount <= 10_000_000) { "가명화 입력 레코드 수가 허용치를 초과했습니다." }
                context
            },
            deidCpuExecutor,
        )
    }

    private fun maskAsync(context: DeidJobContext): CompletableFuture<DeidJobContext> {
        return CompletableFuture.supplyAsync(
            {
                log.info(
                    "가명화 마스킹 파이프라인을 실행합니다. tenantId={}, jobId={}",
                    context.tenantId,
                    context.jobId,
                )
                simulateIoLatency()
                context
            },
            deidIoExecutor,
        )
    }

    private fun simulateIoLatency() {
        try {
            Thread.sleep(100)
        } catch (interruptedException: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun publishStage(
        context: DeidJobContext,
        stage: DeidStage,
        payloadLocation: String,
        errorMessage: String?,
    ): CompletableFuture<Void> {
        val event = DeidJobEvent.newBuilder()
            .setEventId(context.eventId)
            .setOccurredAt(Instant.now())
            .setTenantId(context.tenantId)
            .setJobId(context.jobId)
            .setStage(stage)
            .setPayloadLocation(payloadLocation)
            .setErrorCode(errorMessage?.let { "DEID-PIPELINE-ERROR" })
            .setErrorMessage(errorMessage)
            .build()

        avroMessageConverter.validateRecord(event)
        val payload = avroMessageConverter.serialize(event)
        val topic = requireNotNull(topicProperties.deidJobs) { "deid.jobs 토픽이 설정되어야 합니다." }
        val key = context.key()
        log.info("가명화 파이프라인 이벤트를 게시합니다. topic={}, key={}, stage={}", topic, key, stage)
        return kafkaTemplate.send(topic, key, payload)
            .thenAccept { sendResult ->
                val metadata = sendResult.recordMetadata
                if (metadata != null) {
                    log.debug(
                        "가명화 이벤트 게시 완료. topic={}, partition={}, offset={}",
                        metadata.topic(),
                        metadata.partition(),
                        metadata.offset(),
                    )
                }
            }
    }

    private data class DeidJobContext(
        val tenantId: String,
        val jobId: String,
        val batchId: String,
        val rawPayloadLocation: String,
        val outputPayloadLocation: String,
        val recordCount: Long,
        val eventId: String,
    ) {
        fun key(): String = "$tenantId:$jobId"

        companion object {
            fun from(event: CdwLoadEvent, deidTopic: String): DeidJobContext {
                val tenantId = Objects.requireNonNull(event.tenantId, "tenantId는 null일 수 없습니다.")
                val batchId = Objects.requireNonNull(event.batchId, "batchId는 null일 수 없습니다.")
                val rawLocation = "s3://raw/$tenantId/$batchId"
                val jobId = "${UUID.randomUUID()}-$batchId"
                val outputLocation = "s3://deid/$tenantId/$jobId"
                val recordCount = event.recordCount ?: 0L
                val eventId = Objects.requireNonNull(event.eventId, "eventId는 null일 수 없습니다.")
                // deidTopic은 컨텍스트 생성 시점에 접근만 하여 미리 검증한다.
                require(deidTopic.isNotBlank()) { "deid.jobs 토픽이 설정되어야 합니다." }
                return DeidJobContext(
                    tenantId,
                    jobId,
                    batchId,
                    rawLocation,
                    outputLocation,
                    recordCount,
                    eventId,
                )
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(DeidPipelineService::class.java)
    }
}
