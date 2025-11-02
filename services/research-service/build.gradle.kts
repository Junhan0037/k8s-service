description = "Research 서비스 - 연구 검색 API 스켈레톤"

dependencies {
    // Avro 기반 메시징 계약을 공유하여 프로듀서/컨슈머 구현 시 타입 안정성을 보장한다.
    implementation(project(":platform:messaging-contracts"))
}
