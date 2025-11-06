# syntax=docker/dockerfile:1.4

ARG SERVICE_NAME
ARG JAR_PATH

FROM eclipse-temurin:17-jre AS runtime
ARG SERVICE_NAME
ARG JAR_PATH
WORKDIR /app

# build-and-load-images.sh 스크립트가 전달한 Boot JAR을 이미지에 포함한다.
COPY ${JAR_PATH} ./app.jar

EXPOSE 8080

# 표준 Spring Boot 실행 진입점을 유지해 Kubernetes 헬스 체크와 연동한다.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
