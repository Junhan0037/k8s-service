# syntax=docker/dockerfile:1

# 멀티 스테이지 빌드를 사용해 경량 런타임 이미지를 만든다.
FROM gradle:8.7-jdk17 AS builder
WORKDIR /workspace

# 의존성 캐시를 최대한 활용하기 위해 Gradle 래퍼 및 소스 파일을 구분해 복사한다.
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts ./
COPY gateway/build.gradle.kts gateway/build.gradle.kts
COPY platform ./platform
COPY services ./services
COPY gateway/src ./gateway/src

# 애플리케이션 산출물 생성과 테스트를 동시에 수행해 빌드 안정성을 확보한다.
RUN ./gradlew :gateway:bootJar :gateway:test --no-daemon

# 실제 실행에는 JRE만 필요하므로 경량 베이스 이미지를 사용한다.
FROM eclipse-temurin:17-jre
WORKDIR /app

# 필요한 최소한의 실행 산출물만 복사한다.
COPY --from=builder /workspace/gateway/build/libs/*.jar app.jar

# 외부에서 포트를 명확히 노출한다.
EXPOSE 8080

# Spring Boot 애플리케이션을 실행한다.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
