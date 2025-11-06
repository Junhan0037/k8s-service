plugins {
    // 공통 라이브러리 모듈은 다른 서비스에서 바로 의존하도록 Java Library 플러그인을 사용한다.
    `java-library`
    // BOM 기반 버전 관리를 위해 Spring Dependency Management 플러그인을 적용한다.
    id("io.spring.dependency-management")
}

description = "ResearchEx 공통 라이브러리 (로그/관측성/회복탄력성)"

dependencies {
    // Spring Boot BOM을 먼저 불러와 핵심 스타터 의존성 버전을 일관되게 맞춘다.
    api(platform("org.springframework.boot:spring-boot-dependencies:${rootProject.extra["springBootVersion"]}"))

    // 관측성: 각 서비스가 동일한 Actuator/Prometheus 스택을 사용하도록 공통 스타터를 노출한다.
    api("org.springframework.boot:spring-boot-starter-actuator")
    api("io.micrometer:micrometer-registry-prometheus")
    api("org.springframework.boot:spring-boot-starter-aop")

    // 메시징: Kafka 클라이언트 및 Spring Kafka 통합을 공통 의존성으로 제공한다.
    api("org.springframework.kafka:spring-kafka")
    implementation("org.apache.kafka:kafka-clients")

    // Avro 직렬화를 활용해 계약 기반 메시지 포맷을 유지한다.
    api("org.apache.avro:avro:1.11.3")

    // 관측성: Micrometer 기반 관찰/추적 기능을 기본 제공한다.
    api("io.micrometer:micrometer-observation")
    api("io.micrometer:micrometer-core")
    api("io.micrometer:micrometer-tracing-bridge-brave")
    api("io.zipkin.reporter2:zipkin-reporter-brave")

    // 캐싱/세션: 서비스 전반에 일관된 다단 캐시 구성을 제공하기 위해 Cache/Redis 스타터와 Caffeine을 노출한다.
    api("org.springframework.boot:spring-boot-starter-cache")
    api("org.springframework.boot:spring-boot-starter-data-redis")
    api("com.github.ben-manes.caffeine:caffeine:3.1.8")

    // 회복탄력성: Resilience4j 핵심 컴포넌트와 Micrometer 연계를 사전 구성한다.
    api("io.github.resilience4j:resilience4j-spring-boot3:${rootProject.extra["resilience4jVersion"]}")
    api("io.github.resilience4j:resilience4j-micrometer:${rootProject.extra["resilience4jVersion"]}")
    api("io.github.resilience4j:resilience4j-bulkhead:${rootProject.extra["resilience4jVersion"]}")

    // 로깅: Logback과 JSON 인코더를 포함시켜 서비스 로그 표준화를 돕는다.
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")
    implementation("com.fasterxml.jackson.core:jackson-databind")

    // Spring MVC 필터 및 자동 구성 기능을 사용한다.
    api("org.springframework:spring-web")
    // 전역 예외 처리기 등 MVC 계층 클래스를 노출하기 위해 WebMVC 모듈을 함께 포함한다.
    api("org.springframework:spring-webmvc")
    // Servlet API를 사용하는 필터 구현을 위해 Servlet 모듈을 명시한다.
    compileOnly("jakarta.servlet:jakarta.servlet-api")
    api("org.springframework:spring-context")
    api("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    compileOnly("org.springframework.security:spring-security-core")
    kapt("org.springframework.boot:spring-boot-configuration-processor:${rootProject.extra["springBootVersion"]}")

    // 기본 단위 테스트를 위한 JUnit5 의존성을 등록해 두고 필요 시 확장한다.
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testImplementation("org.springframework.boot:spring-boot-test")
    testImplementation("org.assertj:assertj-core:3.25.3")
    testImplementation(project(":platform:messaging-contracts"))
}
