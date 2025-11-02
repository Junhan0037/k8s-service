# ResearchEx 메시징 계약 모듈

이 모듈은 ResearchEx 플랫폼에서 사용하는 Kafka 메시지 계약을 관리한다.  
Avro 스키마와 토픽 메타데이터를 한 곳에서 유지해 서비스 간 계약 변경을 사전에 검증하고, Schema Registry와의 싱크를 보장한다.

## 구성 요소

- `src/main/avro`: Avro 스키마 정의
  - `ResearchQueryRequested.avsc`: 연구 질의 실행 요청 이벤트
  - `ResearchQueryResult.avsc`: 연구 질의 실행 결과/진행 상태 이벤트
  - `DeidJobEvent.avsc`: 가명화 Job 생성 및 상태 이벤트
  - `CdwLoadEvent.avsc`: CDW 적재 파이프라인 진행 이벤트
- `src/main/resources/kafka/topics.yaml`: 토픽별 파티션/보관/프로듀서/컨슈머/헤더 계약 요약

## 빌드 방법

```bash
./gradlew :platform:messaging-contracts:build
```

- Gradle Avro 플러그인이 스키마를 검증하고, `build/generated-main-avro-java` 경로에 자바 클래스를 생성한다.
- 서비스 모듈은 `implementation(project(":platform:messaging-contracts"))` 의존성을 선언하여 동일한 메시징 계약을 공유한다.

## 운영 가이드

- Schema Registry와 버전 싱크: 스키마 변경 시 `topics.yaml` 주석을 갱신하고, PR 단계에서 팀 리뷰 후 Schema Registry에 `compatibility=BACKWARD` 정책으로 배포한다.
- 토픽 스펙 변경: 파티션/보관 기간 변경은 인프라팀과 조율 후 Terraform 또는 Helm 차트에서 함께 변경한다.
- DLQ 재처리: `retry-count` 헤더가 3 이상인 레코드는 DLQ로 전송하며, 재처리 시 원본 헤더를 유지해야 한다.
