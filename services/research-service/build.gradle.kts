description = "Research 서비스 - 연구 검색 API 스켈레톤"

dependencies {
    // Avro 기반 메시징 계약을 공유하여 프로듀서/컨슈머 구현 시 타입 안정성을 보장한다.
    implementation(project(":platform:messaging-contracts"))
    // 공통 라이브러리에 정의된 다단 캐시/관측성 구성요소를 재사용한다.
    implementation(project(":platform:common-lib"))
    implementation("org.springframework.boot:spring-boot-starter-data-elasticsearch")
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")
    testImplementation("io.projectreactor:reactor-test")
}
