package com.researchex.common.messaging;

import com.researchex.contract.cdw.CdwLoadEvent;
import com.researchex.contract.cdw.CdwLoadStage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AvroMessageConverter의 직렬화/검증 동작을 검증해 메시징 계약 테스트를 보완한다.
 */
class AvroMessageConverterTest {

    private final AvroMessageConverter converter = new AvroMessageConverter();

    @Test
    void serializeAndDeserializeShouldRoundTripAvroRecord() {
        CdwLoadEvent original = sampleEvent();

        byte[] payload = converter.serialize(original);
        CdwLoadEvent restored = converter.deserialize(payload, CdwLoadEvent.class);

        assertThat(restored.getEventId()).isEqualTo(original.getEventId());
        assertThat(restored.getStage()).isEqualTo(CdwLoadStage.RECEIVED);
        assertThat(restored.getRecordCount()).isEqualTo(original.getRecordCount());
    }

    @Test
    void validateRecordShouldRaiseErrorWhenRequiredFieldIsNull() {
        CdwLoadEvent event = sampleEvent();
        event.put(0, null); // eventId 필드를 강제로 null로 설정해 스키마 위반을 유도한다.

        assertThatThrownBy(() -> converter.validateRecord(event))
                .isInstanceOf(AvroSerializationException.class)
                .hasMessageContaining("Avro 레코드가 스키마 제약을 만족하지 않습니다.");
    }

    @Test
    void deserializeShouldFailOnCorruptedPayload() {
        byte[] corrupted = new byte[] {1, 2, 3, 4, 5};

        assertThatThrownBy(() -> converter.deserialize(corrupted, CdwLoadEvent.class))
                .isInstanceOf(AvroSerializationException.class);
    }

    private CdwLoadEvent sampleEvent() {
        return CdwLoadEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now())
                .setTenantId("tenant-x")
                .setBatchId("batch-y")
                .setStage(CdwLoadStage.RECEIVED)
                .setRecordCount(123L)
                .setSourceSystem("CDW")
                .build();
    }
}
