plugins {
    // Avro 기반 스키마를 자바 클래스로 변환하기 위해 전용 플러그인을 적용한다.
    id("com.github.davidmc24.gradle.plugin.avro") version "1.9.1"
}

description = "Kafka 메시징 계약(Schema + Topic 메타데이터)"

dependencies {
    // Avro 스키마 생성을 위한 런타임 라이브러리를 명시하여 코드 생성 시 의존성을 해소한다.
    implementation("org.apache.avro:avro:1.11.3")
}

avro {
    // 표준 logicalType 매핑을 사용하고, 필드 doc 주석까지 포함해 스키마 설명을 유지한다.
    isCreateSetters = false
    fieldVisibility.set("PRIVATE")
}
