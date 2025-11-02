description = "De-identification 서비스 - 가명화 파이프라인 스켈레톤"

dependencies {
    // 가명화 Job 이벤트 컨슈머/프로듀서 구현 시 공통 Avro 스키마를 재사용한다.
    implementation(project(":platform:messaging-contracts"))
}
