# MSA 플랫폼 구현

MSA 기반 플랫폼의 로컬·쿠버네티스 학습용 구현체입니다. `study.md`에서 정의한 목표에 따라 6개의 마이크로서비스, Spring Cloud Gateway, Kafka/Redis/PostgreSQL/Elasticsearch, 관측성 스택까지 한 번에 재현할 수 있도록 구성했습니다.

## 주요 디렉터리
- `gateway/` : Spring Cloud Gateway 기반 API 게이트웨이. JWT 검증과 `X-Trace-Id` 전파를 담당합니다.
- `services/` : 6개 코어 서비스(`research`, `registry`, `mr-viewer`, `deid`, `user-portal`, `cdw-loader`)의 Spring Boot 애플리케이션이 위치합니다.
- `platform/common-lib` : 캐시, 관측성, 회복탄력성, 내부 보안 필터 등을 자동 구성으로 제공하는 공통 라이브러리.
- `platform/messaging-contracts` : Kafka 토픽/Avro 스키마 계약(`topics.yaml`) 모음.
- `platform/docker` : 로컬 개발 인프라(Docker Compose, 초기화 스크립트).
- `platform/k8s` : Minikube 배포용 Kustomize 매니페스트.
- `platform/observability` : Prometheus/Grafana/Zipkin 활용 가이드.
- `samples/` : E2E 학습을 위한 샘플 요청 본문과 README.
- `scripts/` : 이미지 빌드/적재 및 샘플 시나리오 실행 스크립트.

## 아키텍처 하이라이트
- **데이터 파이프라인** : `cdw-loader`가 CDW 배치 적재 이벤트를 Kafka(`cdw.load.events`)에 게시하면 `deid-service`가 가명화 파이프라인을 실행하고, 완료 이벤트(`deid.jobs`)를 `research-service`가 수신해 인메모리 색인/캐시를 갱신합니다.
- **API 게이트웨이** : Spring Cloud Gateway가 모든 외부 API를 통합 노출하며, JWT 필터(`JwtAuthenticationFilter`)와 트레이스 필터(`TraceIdFilter`)로 보안 및 추적성을 유지합니다.
- **캐싱 전략** : `platform/common-lib`의 `MultiTierCacheManager`로 Caffeine(L1) + Redis(L2) 캐시를 일관 적용하고, `research-service`는 검색 결과와 인덱스를 캐싱해 SLA를 맞춥니다.
- **관측성** : Micrometer/Prometheus/Zipkin이 모든 서비스에 기본 내장돼 있으며, `/actuator/prometheus`와 Zipkin 리포터가 기본 활성화되어 있습니다.
- **내부 보호** : `InternalSecretFilter`가 `/internal/**` 경로를 `X-Internal-Secret` 헤더로 보호하고, HTTP 로그는 `SensitiveDataMasker`를 통해 마스킹 후 남깁니다.

## 개발 환경 준비
- JDK 17 (Gradle Wrapper가 자동 사용)
- Docker 및 Docker Compose
- Kafka/Redis 등은 `platform/docker/docker-compose.yml`로 기동
- 선택 사항: Minikube(로컬 Kubernetes 실습용), kubectl, kustomize
- macOS에서 샘플 스크립트 실행 시 `curl`, `jq` 설치 필요

## 로컬 빠른 시작
1. **인프라 기동**
   ```bash
   docker compose -f platform/docker/docker-compose.yml up -d
   ```
2. **마이크로서비스 실행**  
   필요 서비스만 선택 실행할 수 있으며, 기본 포트는 아래와 같습니다.
   ```bash
   ./gradlew :gateway:bootRun                                 # 8080
   ./gradlew :services:research-service:bootRun               # 8101
   ./gradlew :services:registry-service:bootRun               # 8102
   ./gradlew :services:mr-viewer-service:bootRun              # 8103
   ./gradlew :services:deid-service:bootRun                   # 8104
   ./gradlew :services:user-portal:bootRun                    # 8105
   ./gradlew :services:cdw-loader:bootRun                     # 8106
   ```
3. **헬스 체크**  
   각 서비스의 `/actuator/health` 또는 게이트웨이의 `/actuator/info`를 확인합니다.

## 샘플 시나리오 실행
로컬 인프라와 `cdw-loader`, `deid-service`, `research-service`가 기동 중이라면 아래 명령으로 적재→가명화→색인→검색 흐름을 재현할 수 있습니다.
```bash
./scripts/run-sample-scenario.sh
```
- `samples/scenarios/*.json`을 사용해 API 호출을 자동화합니다.
- `RESEARCHEX_INTERNAL_SECRET`가 기본값(`researchex-internal-secret`)과 일치해야 `/internal/research/index` 폴링이 성공합니다.
- 실행이 끝나면 Zipkin(<http://localhost:9411>)과 Grafana(<http://localhost:3000>)에서 트레이스/메트릭을 확인하는 것이 학습 목표입니다.

## 관측성 스택
- Prometheus : <http://localhost:9090>, 설정 파일은 `platform/docker/prometheus/prometheus.yml`
- Grafana : <http://localhost:3000> (ID/PW `researchex/researchex`), 대시보드는 `platform/observability` 참고
- Zipkin : <http://localhost:9411>, 모든 서비스가 `ZIPKIN_ENDPOINT`(기본 `http://zipkin:9411/api/v2/spans`)로 스팬을 전송
- 공통 메트릭 : `http.server.requests`, `researchex.cache.*`, `kafka_consumer_records_lag_max` 등

## Kafka 계약 요약 (`platform/messaging-contracts/src/main/resources/kafka/topics.yaml`)
- `research.query.request` : 사용자 포털 → 연구 질의 워커
- `research.query.result` : 연구 질의 워커 → 사용자 포털 SSE
- `deid.jobs` : CDW Loader → Deid 워커 (가명화 파이프라인 상태)
- `cdw.load.events` : CDW Loader → Deid/관측성 (배치 적재 진행 상황)

## 공통 라이브러리 주요 기능 (`platform/common-lib`)
- **CacheAutoConfiguration** : Caffeine + Redis 다단 캐시와 메트릭(`researchex.cache.*`) 자동화.
- **TracingAutoConfiguration** : `X-Trace-Id` 필터, HTTP 로깅 필터, Zipkin 리포팅을 손쉽게 활성화.
- **InternalSecurityAutoConfiguration** : `researchex.security.secret`가 설정되면 내부 시크릿 필터를 주입.
- **KafkaInfrastructureAutoConfiguration** : `AvroMessageConverter`와 Kafka 템플릿/리스너 팩토리를 표준화.
- **ResilienceAutoConfiguration** : Resilience4j 설정과 `MdcPropagatingTaskDecorator`로 비동기 컨텍스트 전파.
- **GlobalExceptionHandler** : `ErrorResponse(code, message, traceId)` 규격으로 예외 응답을 통일.

## 서비스별 특징
- **research-service** : Elasticsearch 검색 + Redis/Caffeine 캐시 + SSE 진행률(`ResearchProgressController`), 내부 인덱스 조회용 `/internal/research/index` 제공.
- **registry-service / mr-viewer-service** : 학습용 스켈레톤. 향후 도메인 API 확장을 위한 베이스.
- **deid-service** : Kafka 리스너(`CdwLoadEventListener`)가 CDW 이벤트를 수신해 가명화 파이프라인을 비동기로 실행하고 `deid.jobs` 토픽에 결과 이벤트를 게시.
- **cdw-loader** : `POST /api/cdw/batches`를 받아 검증→영속화 단계를 거치며 Kafka 이벤트를 발행.
- **user-portal** : Spring Session + Redis로 분산 세션을 구성하고, JSON 직렬화를 기본값으로 사용.
- **gateway** : JWT 검증, `X-Auth-Subject` 전달, 서비스 라우팅/StripPrefix 필터 정의.

## Kubernetes (Minikube) 배포
1. Minikube 설치 및 기동 : `minikube start --cpus=4 --memory=8192 --addons=ingress,metrics-server`
2. 이미지 빌드 : `./scripts/build-and-load-images.sh` (Gradle BootJar → Docker 이미지 → `minikube image load`)
3. 배포 : `kubectl apply -k platform/k8s/overlays/minikube`
4. 상태 확인 : `kubectl get pods -n research-ex`, 인그레스는 `platform/k8s/base/gateway/ingress.yaml` 참조
자세한 환경 설정은 `platform/k8s/README.md`를 참고하세요.

## 테스트 및 품질 점검
- 전체 테스트 : `./gradlew test`
- 특정 서비스 : `./gradlew :services:research-service:test`
- 주요 시나리오
  - `CdwLoadPipelineServiceIntegrationTest` : CDW 적재 파이프라인 E2E 검증
  - `ResearchQueryControllerTest` : 검색 컨트롤러 검증
  - `InternalGatewayHeaderProviderTest` 등 공통 라이브러리 테스트

## 핵심 환경 변수
- `GATEWAY_JWT_HMAC_SECRET` : 게이트웨이 JWT 서명 검증 키 (필수로 교체 필요)
- `RESEARCHEX_INTERNAL_SECRET` : `/internal/**` 보호용 시크릿, `InternalSecretFilter`와 샘플 스크립트가 사용
- `ZIPKIN_ENDPOINT`, `RESEARCHEX_ENV` : 관측성 엔드포인트·환경 태그
- `REDIS_HOST`, `REDIS_PORT`, `ELASTICSEARCH_URIS`, `KAFKA_BOOTSTRAP_SERVERS` : 각 서비스의 외부 인프라 연결 정보
- `USER_PORTAL_BASE_URL`, `MR_VIEWER_BASE_URL` 등 게이트웨이/Feign/WebClient 대상 URL

## 추가 참고 자료
- `study.md` : 단계별 구현 목표와 백로그
- `samples/README.md` : 샘플 데이터/스크립트 설명
- `platform/observability/README.md` : Prometheus/Grafana/Zipkin 활용 가이드
- `platform/k8s/README.md` : Minikube 환경 상세 설정

학습을 마친 뒤에는 `study.md`의 백로그 항목(멀티테넌트 격리, 서비스 메시, 배포 전략 등)을 기반으로 실습 범위를 확장해보자.
