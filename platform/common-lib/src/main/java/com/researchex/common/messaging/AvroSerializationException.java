package com.researchex.common.messaging;

/** Avro 직렬화/역직렬화 과정에서 발생한 예외를 표현한다. */
public class AvroSerializationException extends RuntimeException {

  public AvroSerializationException(String message) {
    super(message);
  }

  public AvroSerializationException(String message, Throwable cause) {
    super(message, cause);
  }
}
