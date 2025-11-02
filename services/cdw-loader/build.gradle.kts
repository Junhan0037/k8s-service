description = "CDW Loader 서비스 - 데이터 적재 파이프라인 스켈레톤"

dependencies {
    // CDW 적재 이벤트와 가명화 Job 요청 이벤트 모두 공통 Avro 스키마를 사용하도록 계약 모듈에 의존한다.
    implementation(project(":platform:messaging-contracts"))
}
