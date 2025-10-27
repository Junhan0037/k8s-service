## ResearchEx 구현 가이드 (Local + Minikube)

### 1) 목표와 범위
- 목표: ResearchEx(멀티테넌트 MSA 기반 임상 데이터 연구 플랫폼)를 로컬 환경에서 재현 가능한 수준으로 구현·검증하고, Minikube 기반 Kubernetes 환경까지 확장 학습한다.
- 범위: 코어 마이크로서비스 6종, 이벤트 드리븐 기반 메시징, API Gateway, 인증/인가, 캐싱/세션, 관측성(로그·메트릭·트레이싱), 기본 데이터 계층을 로컬에서 구동한다. Minikube 상에서 동일 구성을 배포·운영하는 방법을 실습한다.
- 비범위: 퍼블릭 클라우드 관리형 서비스(EKS/HPA 등), 수십만 동시 사용자 부하 테스트, 병원별 멀티 테넌트 운영 자동화. Kubernetes 오토스케일링/서비스 메시 등 고급 주제는 백로그로 분리한다.

### 2) 로컬 아키텍처 개요
- 서비스: Research(연구검색), Registry(레지스트리), MR Viewer(의무기록뷰어), De-identification(가명화), User Portal(사용자 포털), CDW Loader(데이터 적재)는 학습용으로 간단하게 구현
- 게이트웨이/보안: Nginx(또는 Spring Cloud Gateway) + JWT, 내부 API 보호(`X-Internal-Secret`)
- 메시징: Kafka(+Zookeeper)
- 데이터: PostgreSQL(OLTP), Elasticsearch(검색), Redis(Cache/Session)
- 관측성: Zipkin(트레이싱), Prometheus + Grafana(메트릭), Loki/ELK(선택, 로그)
- 컨테이너 오케스트레이션: Docker Compose(단일 브릿지 네트워크) / Minikube 기반 Kubernetes(심화 실습)
- Kubernetes 구성요소: Nginx Ingress Controller, Metrics Server, ConfigMap/Secret, StatefulSet/Deployment, Helm 또는 Kustomize

### 3) 디렉터리 구조 제안(모노레포)
- `gateway/` API Gateway(Nginx)
- `services/`
  - `research-service/` 검색/질의 처리
  - `registry-service/` 레지스트리 관리
  - `mr-viewer-service/` 의무기록 조회
  - `deid-service/` 가명화 파이프라인
  - `user-portal/` 사용자/권한/세션
  - `cdw-loader/` CDW 적재 배치/스트리밍
- `platform/`
  - `common-lib/` 공통 라이브러리(TraceId, 에러, 보안 인터셉터, Feign/WebClient 설정, Resilience4j)
  - `docker/` Compose 파일과 초기 스크립트
  - `observability/` 대시보드, 알람 룰, Zipkin/Prometheus 설정
  - `k8s/` Minikube용 매니페스트 또는 Helm 차트

### 4) 구현 순서(로컬 기준 단계별)
1. 프로젝트 스캐폴딩
   - 모노레포 루트 Gradle 초기화, 자바 17 표준 설정, 공통 Checkstyle/Spotless 도입
   - `platform/common-lib`에 공통 의존성 BOM, Logback, Micrometer, Sleuth/Brave, Resilience4j 설정
2. 로컬 인프라 도커 컴포즈
   - `platform/docker/docker-compose.yml` 작성: `kafka`, `zookeeper`, `redis`, `postgres`, `elasticsearch`, `zipkin`, `prometheus`, `grafana`
   - 네트워크/볼륨/초기 스크립트(`init/*.sql`, 토픽 생성 스크립트) 준비
3. 로컬 Kubernetes(Minikube) 환경 구성
   - Minikube 설치 후 `minikube start --cpus=4 --memory=8192 --addons=ingress,metrics-server` 명령으로 클러스터 기동
   - Docker Compose 정의를 Helm Chart 또는 Kustomize 템플릿으로 변환하고 `platform/k8s` 디렉터리에 배치
   - Kafka/PostgreSQL/Redis 등 상태 저장 워크로드를 StatefulSet + PersistentVolumeClaim으로 정의, 기본 StorageClass는 `standard`(Docker driver) 사용
   - Zipkin/Prometheus/Grafana는 Helm 차트 활용 또는 자체 매니페스트로 배포, ServiceMonitor와 Ingress 리소스 준비
   - Ingress로 `/api/**`, `/grafana`, `/zipkin` 등을 노출하고, `minikube tunnel`을 통해 호스트 포트를 확보
4. API Gateway
   - Nginx: `/api/research/**`, `/api/registry/**`, ... 라우팅
   - JWT 검증(공개키/비밀키)와 요청 헤더 `X-Trace-Id` 전달
5. 보안/공통 모듈
   - `X-Internal-Secret` 인터셉터, `/internal/**` 보호(Spring Security Filter + Properties 기반 시크릿)
   - 공통 에러 규격(`code`, `message`, `traceId`), 전역 예외 처리
   - TraceId MDC 주입, 요청/응답 로깅(개인정보 마스킹 룰 포함)
6. 서비스 스켈레톤(동일 패턴 템플릿)
   - Research, Registry, MR Viewer, Deid, User Portal, CDW Loader 각각 Spring Boot 앱 생성
   - 헬스체크(`/actuator/health`), 기본 `/api/**` 컨트롤러, 공통 라이브러리 적용
7. 데이터 계층 연결
   - PostgreSQL 스키마/DDL(`platform/docker/init/pg.sql`), Flyway/Liquibase 적용
   - Elasticsearch 인덱스 템플릿/매핑 스크립트, 샘플 데이터 적재 스크립트
8. 메시징 계약 정의
   - Kafka 토픽 명세: `research.query.request`, `research.query.result`, `deid.jobs`, `cdw.load.events` 등
   - 메시지 스키마(JSON/Avro 중 택1), 키 전략, 리트라이/재처리 정책
9. 비동기 파이프라인 구현
   - CDW Loader → Kafka → Deid → Research 인덱싱(ES) 흐름 구현
   - CompletableFuture/WebFlux로 I/O 비동기화, 커스텀 ThreadPool 분리(IO/CPU)
10. 캐싱/세션
    - Redis L2 캐시 전략(정적: 1h TTL, 동적쿼리: 10m TTL), Spring Cache(Caffeine) L1
    - Spring Session Redis로 세션 중앙화(User Portal)
11. 회복탄력성/패턴
    - Resilience4j CircuitBreaker/Retry/RateLimiter, Fallback 응답 적용
    - 멱등성 키(헤더 또는 파라미터) 기반 Redis로 중복 처리 방지
12. 내부 통신 게이트웨이 패턴
    - `UserGateway`, `MedicalRecordGateway` 인터페이스 + `WebClient/Feign` 구현체 분리
    - 서비스 간 HTTP 호출/헤더(TraceId, Internal-Secret) 전파 일원화
13. 관측성
    - Zipkin 연동, Micrometer + Prometheus 스크레이프, Grafana 대시보드 임포트
    - 서비스별 핵심 지표(요청 지연, 에러율, 캐시 히트율, 토픽 컨슈머 랙)
14. 검색/조회 최적화
    - Research 서비스: Redis 캐시 + ES 쿼리, 페이지네이션, 정렬/필터, N+1 방지
    - 응답 SLA(<= 1s) 목표 모니터링 지표로 연계
15. SSE/실시간 알림(선택)
    - Redis Pub/Sub 또는 서버내 이벤트 버스로 작업 진행률 스트리밍(`/api/research/progress`)
16. 샘플 데이터/시나리오
    - 더미 환자/연구/의무기록 데이터, 쿼리 시나리오, 가명화 잡 샘플
    - E2E 플로우(적재→가명화→색인→검색) 검증 스크립트
17. 테스트 전략
    - 계층별 테스트: 단위(JUnit5), 슬라이스(WebMvc, DataJpa), 컨테이너 테스트(Testcontainers로 Kafka/Redis/PG)
    - 컨슈머/프로듀서 계약 테스트(컨트랙트 테스트 또는 Schema 호환성 체크)
18. 개발자 경험(DX)
    - `Makefile` 또는 Gradle Task: `make up`, `make down`, `make seed`, `make test`
    - 샘플 환경변수 `.env.example`, 로컬 프로파일 `application-local.yml`

### 5) 서비스별 최소 기능(MVP)
- Research Service
  - REST: `POST /api/research/query`(쿼리 제출, Kafka에 요청 게시), `GET /api/research/result/{id}`
  - ES 연동, Redis 캐시, 트레이싱/로그
- Registry Service
  - 등록/수정/조회 CRUD, PostgreSQL + 캐싱
- MR Viewer Service
  - 환자의 의무기록 요약/상세 조회(가상의 FHIR-like 스키마), 내부 API 인증 필수
- Deid Service
  - 입력 이벤트 처리(Kafka), PII 마스킹/토큰화 룰, 결과 이벤트 게시
- User Portal
  - 로그인/토큰 발급(JWT), 사용자/권한, Spring Session Redis
- CDW Loader
  - 배치/스트리밍로 데이터 적재 → Kafka 이벤트 발행

### 6) 메시지/계약 초안
- 토픽 규칙: `<bounded-context>.<entity>.<event>` 또는 `<context>.<command|event>` 일관성 유지
- 공통 헤더: `traceId`, `sourceService`, `idempotencyKey`, `timestamp`
- 오류 채널: `*.dlq` 운영, 재처리 정책(Backoff, MaxAttempts)

### 7) 로컬 실행 순서
1. 도커 인프라 기동: `docker compose -f platform/docker/docker-compose.yml up -d`
2. 토픽/스키마/DDL 초기화 스크립트 실행
3. 각 서비스 `local` 프로파일로 기동(포트 중복 주의)
4. Minikube 기반 Kubernetes 실습
   - `minikube start`로 클러스터 기동 후 `kubectl get pods -A`로 상태 확인
   - `kubectl apply -k platform/k8s/overlays/local` 또는 Helm 차트를 통해 인프라/서비스 배포
   - `minikube tunnel` 또는 `minikube service <name> --url`로 접근 경로 확보
5. 게이트웨이(Nginx/SCG) 기동 후 라우팅 확인
6. 샘플 시나리오 실행
   - `POST /api/research/query` → 진행률(SSE) 확인 → 결과 조회
   - CDW Loader → Deid → Research 색인 연동 검증
7. 관측성 점검: Zipkin 트레이스, Grafana 대시보드, 컨슈머 랙 확인 (Kubernetes에서는 `kubectl port-forward` 또는 Ingress 경로 활용)

### 8) 체크리스트 & 완료 기준(DoD)
- 기능
  - [ ] 6개 서비스 기동 및 헬스 체크 통과
  - [ ] 게이트웨이 라우팅/인증/내부 시크릿 보호 동작
  - [ ] Kafka 비동기 파이프라인 E2E 처리 성공
  - [ ] Redis 캐시/세션 동작 및 캐시 히트율 지표 노출
  - [ ] (선택) Minikube 환경에서 핵심 서비스 1~2종 배포 및 Ingress 라우팅 확인
- 비기능
  - [ ] 요청당 TraceId 전파 및 Zipkin 트레이스 확인
  - [ ] Prometheus 노출 지표 수집 및 Grafana 대시보드 시현
  - [ ] Resilience4j 설정(CB/Retry/RateLimiter) 유효성 확인
  - [ ] 멱등성/중복처리 방지 통과(동일 키 재시도 테스트)
  - [ ] (선택) Kubernetes 리소스(HPA/StatefulSet) 배포 시 리소스 요청/제한 설정 검증
- 테스트/품질
  - [ ] 핵심 유스케이스 단위/슬라이스/컨테이너 테스트 통과
  - [ ] 컨슈머/프로듀서 계약 또는 스키마 호환성 검증 통과

### 9) 백로그(고도화 항목)
- gRPC 전환 대비 Gateway 인터페이스 확장
- 멀티테넌트 격리 전략(스키마/DB/토픽 분리) 평가 및 PoC
- 정책 기반 PII 가명화 룰 엔진화 및 UI 관리
- ES 쿼리 옵티마이저/검색 랭킹 고도화, 컬럼형 DB(Vertica) 연동 PoC
- Rate limiting(전역/고객사/사용자 단위), 서킷 오픈 알람 자동화
- 카나리/블루그린 배포 전략의 로컬 시뮬레이션 파이프라인 마련
- Kubernetes 심화: Helm 차트 모듈화, Argo CD 도입, Istio/Linkerd 서비스 메시 실험

### 10) 참고
- 기술 스택: Java 17, Spring Boot, Kafka, Redis, Docker, Elasticsearch, PostgreSQL, Resilience4j, Micrometer, Zipkin, Prometheus, Grafana, Kubernetes(Minikube), Helm/Kustomize
- 보안: JWT, 내부 시크릿 헤더, 개인정보 마스킹/로깅 정책, Kubernetes Secret/NetworkPolicy 기초
