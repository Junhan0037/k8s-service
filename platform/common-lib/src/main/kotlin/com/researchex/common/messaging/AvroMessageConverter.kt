package com.researchex.common.messaging

import org.apache.avro.Schema
import org.apache.avro.io.DecoderFactory
import org.apache.avro.io.EncoderFactory
import org.apache.avro.specific.SpecificDatumReader
import org.apache.avro.specific.SpecificDatumWriter
import org.apache.avro.specific.SpecificData
import org.apache.avro.specific.SpecificRecord
import org.apache.avro.specific.SpecificRecordBase
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Avro SpecificRecord 객체를 Kafka 전송에 사용할 바이트 배열로 변환/역변환한다.
 * 스키마 레지스트리가 없는 로컬 개발 환경에서도 Avro 계약을 준수하도록 하기 위해 사용한다.
 */
class AvroMessageConverter {

    private val decoderFactory: DecoderFactory = DecoderFactory.get()
    private val encoderFactory: EncoderFactory = EncoderFactory.get()

    /**
     * Avro SpecificRecord를 바이트 배열로 직렬화한다.
     *
     * @param record Kafka로 전송할 Avro SpecificRecord
     * @return Avro 바이너리 인코딩 결과 바이트 배열
     */
    fun serialize(record: SpecificRecord?): ByteArray {
        requireNotNull(record) { "직렬화할 Avro 레코드가 null입니다." }

        return ByteArrayOutputStream().use { outputStream ->
            try {
                val schema: Schema = record.schema
                val writer = SpecificDatumWriter<SpecificRecord>(schema)
                val encoder = encoderFactory.directBinaryEncoder(outputStream, null)
                writer.write(record, encoder)
                encoder.flush()
                outputStream.toByteArray()
            } catch (exception: Exception) {
                log.error(
                    "Avro 레코드 직렬화에 실패했습니다. recordType={}",
                    record.javaClass.name,
                    exception
                )
                throw AvroSerializationException("Avro 레코드 직렬화에 실패했습니다.", exception)
            }
        }
    }

    /**
     * Kafka에서 수신한 Avro 바이너리 데이터를 SpecificRecord 인스턴스로 역직렬화한다.
     *
     * @param payload Avro 바이너리 데이터
     * @param targetClass 역직렬화 대상 Avro SpecificRecord 타입
     */
    fun <T : SpecificRecordBase> deserialize(payload: ByteArray?, targetClass: Class<T>): T {
        requireNotNull(payload) { "역직렬화할 페이로드가 null입니다." }

        return ByteArrayInputStream(payload).use { inputStream ->
            try {
                val template = targetClass.getDeclaredConstructor().newInstance()
                val schema = template.schema
                val reader = SpecificDatumReader<T>(schema)
                val decoder = decoderFactory.directBinaryDecoder(inputStream, null)
                reader.read(null, decoder)
            } catch (exception: java.io.IOException) {
                log.error("Avro 레코드 역직렬화에 실패했습니다. targetType={}", targetClass.name, exception)
                throw AvroSerializationException("Avro 레코드 역직렬화에 실패했습니다.", exception)
            } catch (reflectionError: ReflectiveOperationException) {
                log.error(
                    "Avro SpecificRecord 인스턴스를 생성할 수 없습니다. targetType={}",
                    targetClass.name,
                    reflectionError
                )
                throw AvroSerializationException("Avro SpecificRecord 인스턴스 생성에 실패했습니다.", reflectionError)
            }
        }
    }

    /**
     * 역직렬화된 레코드가 스키마 제약을 준수하는지 검증한다.
     */
    fun validateRecord(record: SpecificRecord) {
        val schema = record.schema
        val specificData = if (record is SpecificRecordBase) {
            record.specificData
        } else {
            SpecificData.get()
        }

        schema.fields.forEach { field ->
            val value = record.get(field.pos())
            if (!specificData.validate(field.schema(), value)) {
                throw AvroSerializationException(
                    "Avro 레코드가 스키마 제약을 만족하지 않습니다. field=${field.name()}, value=$value"
                )
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AvroMessageConverter::class.java)
    }
}
