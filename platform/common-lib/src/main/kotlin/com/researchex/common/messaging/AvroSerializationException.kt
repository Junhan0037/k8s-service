package com.researchex.common.messaging

/** Avro 직렬화/역직렬화 과정에서 발생한 예외를 표현한다. */
class AvroSerializationException : RuntimeException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}
