# 샘플 데이터 & 시나리오

이 디렉터리는 Step 16(샘플 데이터/시나리오) 구현 산출물을 모아둔 공간이다. 로컬 학습 환경에서 멀티 서비스 연동을 빠르게 검증할 수 있도록 더미 데이터와 실행 스크립트를 제공한다.

## 구성 파일
- `scenarios/cdw-batch-request.json`: CDW Loader에 전송할 배치 적재 요청 본문 예시. `tenantId`, `batchId`, `sourceSystem`, `recordCount`를 명시한다.
- `scenarios/deid-job-event.json`: Deid 서비스가 Kafka에 게시하는 `DeidJobEvent` 페이로드 예시. Avro 스키마 필드 구성을 이해하는 참고 자료로 활용한다.
- `scenarios/research-search-request.json`: Research 검색 API 호출 파라미터 예시. 질의어, 필터, 정렬, 페이지 크기를 정의한다.

## 실행 방법
샘플 시나리오 스크립트는 `scripts/run-sample-scenario.sh`에 위치한다. Kafka/Redis/PostgreSQL/Elasticsearch가 기동되어 있고 CDW Loader, Deid 서비스, Research 서비스가 `local` 프로파일로 실행 중이라는 가정하에 다음 명령을 수행한다.

```bash
./scripts/run-sample-scenario.sh
```

스크립트는 아래 순서로 E2E 흐름을 검증한다.
1. **적재 요청**: `cdw-batch-request.json`을 바탕으로 CDW Loader에 `POST /api/cdw/batches` 호출
2. **가명화/색인 대기**: Research 서비스의 `GET /internal/research/index`를 폴링해 Deid → Research 인덱싱 결과 확인
3. **검색 검증**: `research-search-request.json`을 이용해 `GET /api/research/search`를 호출하고 검색 결과/페이징 정보를 출력

### 환경 변수
- `CDW_BASE_URL`: CDW Loader 엔드포인트 기본값(`http://localhost:8106`)
- `RESEARCH_BASE_URL`: Research 서비스 엔드포인트 기본값(`http://localhost:8101`)
- `SCENARIO_POLL_INTERVAL`: 인덱스 폴링 간격(초 단위, 기본 2초)
- `SCENARIO_MAX_ATTEMPTS`: 인덱스 폴링 최대 횟수(기본 15회)
- `RESEARCHEX_INTERNAL_SECRET`: `/internal/**` 엔드포인트 호출 시 사용할 시크릿 헤더 값(기본 `researchex-internal-secret`)

### 후속 확인
시나리오 실행 후 Zipkin에서 Trace를 조회하고, Prometheus/Grafana 대시보드로 레이턴시/메트릭을 살펴보면 Step 16의 학습 목표를 보다 입체적으로 체험할 수 있다.
