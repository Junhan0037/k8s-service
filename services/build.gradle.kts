import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.gradle.jvm.tasks.Jar
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    // 모든 서비스 모듈에 동일 버전의 Spring Boot 플러그인을 적용하기 위해 사전 선언한다.
    id("org.springframework.boot") version "3.2.5" apply false
    // 의존성 관리를 통일하여 예측 가능한 빌드를 보장한다.
    id("io.spring.dependency-management") apply false
}

subprojects {
    // 서비스 모듈은 실행 가능한 애플리케이션 형태이므로 Spring Boot 플러그인을 사용한다.
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")

    extensions.configure<DependencyManagementExtension> {
        imports {
            mavenBom("org.springframework.cloud:spring-cloud-dependencies:2023.0.1")
        }
    }

    dependencies {
        // 공통 라이브러리에 정의된 로깅/관측성/보안 구성을 재사용한다.
        implementation(project(":platform:common-lib"))
        // REST API 노출을 위한 핵심 스타터들을 표준으로 지정한다.
        implementation("org.springframework.boot:spring-boot-starter-actuator")
        implementation("org.springframework.boot:spring-boot-starter-validation")
        implementation("org.springframework.boot:spring-boot-starter-web")

        testImplementation("org.springframework.boot:spring-boot-starter-test")
    }

    tasks.named<BootJar>("bootJar") {
        // 부트 아카이브만 생성하고 중복되는 분류자는 제거한다.
        archiveClassifier.set("")
    }

    tasks.named<Jar>("jar") {
        // Spring Boot 앱은 일반 Jar를 배포하지 않으므로 기본 Jar 생성을 비활성화한다.
        enabled = false
    }

}
