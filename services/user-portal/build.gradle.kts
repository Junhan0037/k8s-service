description = "User Portal 서비스 - 사용자 및 세션 관리 스켈레톤"

dependencies {
    // Kafka 메시지 프로듀서 구현 시 공통 Avro 스키마를 활용하기 위해 계약 모듈을 의존한다.
    implementation(project(":platform:messaging-contracts"))
}
