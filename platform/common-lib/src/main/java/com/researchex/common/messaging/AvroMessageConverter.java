package com.researchex.common.messaging;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;
import org.apache.avro.specific.SpecificRecordBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Avro SpecificRecord 객체를 Kafka 전송에 사용할 바이트 배열로 변환/역변환한다.
 * 스키마 레지스트리가 없는 로컬 개발 환경에서도 Avro 계약을 준수하도록 하기 위해 사용한다.
 */
public class AvroMessageConverter {

  private static final Logger log = LoggerFactory.getLogger(AvroMessageConverter.class);

  private final DecoderFactory decoderFactory = DecoderFactory.get();
  private final EncoderFactory encoderFactory = EncoderFactory.get();

  /**
   * Avro SpecificRecord를 바이트 배열로 직렬화한다.
   *
   * @param record Kafka로 전송할 Avro SpecificRecord
   * @return Avro 바이너리 인코딩 결과 바이트 배열
   */
  public byte[] serialize(SpecificRecord record) {
    if (record == null) {
      throw new IllegalArgumentException("직렬화할 Avro 레코드가 null입니다.");
    }

    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      Schema schema = record.getSchema();
      SpecificDatumWriter<SpecificRecord> writer = new SpecificDatumWriter<>(schema);
      Encoder binaryEncoder = encoderFactory.directBinaryEncoder(outputStream, null);
      writer.write(record, binaryEncoder);
      binaryEncoder.flush();
      return outputStream.toByteArray();
    } catch (IOException exception) {
      log.error("Avro 레코드 직렬화에 실패했습니다. recordType={}", record.getClass().getName(), exception);
      throw new AvroSerializationException("Avro 레코드 직렬화에 실패했습니다.", exception);
    }
  }

  /**
   * Kafka에서 수신한 Avro 바이너리 데이터를 SpecificRecord 인스턴스로 역직렬화한다.
   *
   * @param payload Avro 바이너리 데이터
   * @param targetClass 역직렬화 대상 Avro SpecificRecord 타입
   * @param <T> Avro SpecificRecord 타입 파라미터
   * @return 역직렬화된 Avro SpecificRecord 인스턴스
   */
  public <T extends SpecificRecordBase> T deserialize(byte[] payload, Class<T> targetClass) {
    if (payload == null) {
      throw new IllegalArgumentException("역직렬화할 페이로드가 null입니다.");
    }

    try (ByteArrayInputStream inputStream = new ByteArrayInputStream(payload)) {
      T template = targetClass.getDeclaredConstructor().newInstance();
      Schema schema = template.getSchema();

      SpecificDatumReader<T> reader = new SpecificDatumReader<>(schema);
      BinaryDecoder decoder = decoderFactory.directBinaryDecoder(inputStream, null);
      return reader.read(null, decoder);
    } catch (IOException exception) {
      log.error("Avro 레코드 역직렬화에 실패했습니다. targetType={}", targetClass.getName(), exception);
      throw new AvroSerializationException("Avro 레코드 역직렬화에 실패했습니다.", exception);
    } catch (ReflectiveOperationException reflectionError) {
      log.error(
          "Avro SpecificRecord 인스턴스를 생성할 수 없습니다. targetType={}",
          targetClass.getName(),
          reflectionError);
      throw new AvroSerializationException("Avro SpecificRecord 인스턴스 생성에 실패했습니다.", reflectionError);
    }
  }

  /**
   * 역직렬화된 레코드가 스키마 제약을 준수하는지 검증한다.
   *
   * @param record 검증 대상 Avro SpecificRecord
   */
  public void validateRecord(SpecificRecord record) {
    Schema schema = record.getSchema();
    if (!GenericData.get().validate(schema, record)) {
      throw new AvroSerializationException("Avro 레코드가 스키마 제약을 만족하지 않습니다.");
    }
  }
}
