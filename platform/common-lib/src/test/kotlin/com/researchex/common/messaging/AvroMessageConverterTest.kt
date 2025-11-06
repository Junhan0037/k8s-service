package com.researchex.common.messaging

import com.researchex.contract.cdw.CdwLoadEvent
import com.researchex.contract.cdw.CdwLoadStage
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * AvroMessageConverter의 직렬화/검증 동작을 검증해 메시징 계약 테스트를 보완한다.
 */
class AvroMessageConverterTest {

    private val converter = AvroMessageConverter()

    @Test
    fun serializeAndDeserializeShouldRoundTripAvroRecord() {
        val original = sampleEvent()

        val payload = converter.serialize(original)
        val restored = converter.deserialize(payload, CdwLoadEvent::class.java)

        assertThat(restored.eventId).isEqualTo(original.eventId)
        assertThat(restored.stage).isEqualTo(CdwLoadStage.RECEIVED)
        assertThat(restored.recordCount).isEqualTo(original.recordCount)
    }

    @Test
    fun validateRecordShouldRaiseErrorWhenRequiredFieldIsNull() {
        val event = sampleEvent()
        event.put(0, null)

        assertThatThrownBy { converter.validateRecord(event) }
            .isInstanceOf(AvroSerializationException::class.java)
            .hasMessageContaining("Avro 레코드가 스키마 제약을 만족하지 않습니다.")
    }

    @Test
    fun deserializeShouldFailOnCorruptedPayload() {
        val corrupted = byteArrayOf(1, 2, 3, 4, 5)

        assertThatThrownBy { converter.deserialize(corrupted, CdwLoadEvent::class.java) }
            .isInstanceOf(AvroSerializationException::class.java)
    }

    private fun sampleEvent(): CdwLoadEvent {
        return CdwLoadEvent.newBuilder()
            .setEventId(UUID.randomUUID().toString())
            .setOccurredAt(Instant.now())
            .setTenantId("tenant-x")
            .setBatchId("batch-y")
            .setStage(CdwLoadStage.RECEIVED)
            .setRecordCount(123L)
            .setSourceSystem("CDW")
            .build()
    }
}
