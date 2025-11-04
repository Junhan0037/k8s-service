# ResearchEx 관측성 가이드

ResearchEx 모노레포의 13단계(관측성) 구현에 따라 각 마이크로서비스는 다음 구성을 기본 제공한다.

## 계측 구성 요약
- **Micrometer + Prometheus**: 모든 서비스는 `http.server.requests`, `researchex.cache.*`, `kafka_consumer_records_lag_max` 등의 지표를 `/actuator/prometheus` 엔드포인트에서 노출한다.
- **Zipkin(Brave)**: `management.zipkin.tracing.endpoint` 기본값은 `http://zipkin:9411/api/v2/spans`이며, `ZIPKIN_ENDPOINT` 환경 변수를 통해 재정의할 수 있다.
- **공통 태그**: 모든 메트릭과 Observation에는 `service=<spring.application.name>` 태그가 자동 부여된다.
- **Kafka 관측성**: 공통 Kafka Listener factory가 Micrometer를 활성화해 각 컨슈머의 랙·처리량 지표를 적재한다.
- **캐시 관측성**: 다단 캐시(L1/L2)에 대한 히트율, L2 의존율, 미스 카운터를 `researchex.cache.*` 네임스페이스로 노출한다.

## 로컬 실행 순서
1. 인프라 기동  
   ```bash
   docker compose -f platform/docker/docker-compose.yml up -d prometheus grafana zipkin
   ```
2. 애플리케이션 실행  
   각 서비스는 `/actuator/prometheus`와 Zipkin 에이전트를 자동 노출한다.  
   Zipkin UI: <http://localhost:9411>  
   Prometheus: <http://localhost:9090>  
   Grafana: <http://localhost:3000> (admin ID/PW는 `researchex/researchex`)
3. 대시보드 확인  
   Grafana의 `ResearchEx Observability` 대시보드에서 서비스별 SLA, 오류율, 캐시 히트율, Kafka 컨슈머 랙을 확인한다.

## 주요 Prometheus 지표
| Metric | 설명 |
| --- | --- |
| `http_server_requests_seconds_bucket` | HTTP 요청 지연 히스토그램. `/actuator/**` 경로는 쿼리 조건에서 제외. |
| `researchex_cache_hit_ratio` | 다단 캐시 전체 히트율 (0~1). |
| `researchex_cache_l2_dependency` | 히트 중 L2(Redis)가 차지하는 비율. |
| `researchex_cache_miss_count_total` | 5분 rate를 통해 캐시 미스 처리량 파악 가능. |
| `kafka_consumer_records_lag_max` | 컨슈머별 최대 레코드 랙. SLA 초과 시 알람 조건으로 활용. |

## Zipkin 트레이스 확인 팁
- HTTP 컨트롤러 메서드와 Kafka 리스너에는 `@Observed`를 적용해 에러 발생 시 자동으로 태깅된다.
- TraceId는 `X-Trace-Id` 헤더로 전달되며, gateway와 각 서비스 로그 패턴에 동일하게 출력된다.

## 설정 커스터마이징
- 공통 환경 변수
  - `ZIPKIN_ENDPOINT`: Zipkin 수집 URL (기본값: `http://zipkin:9411/api/v2/spans`)
  - `RESEARCHEX_ENV`: Prometheus/Grafana 태그로 노출되는 환경 값 (기본값: `local`)
- 서비스별 SLA 조정  
  `management.metrics.distribution.slo.http.server.requests` 프로퍼티를 확장하면 서비스 SLA를 손쉽게 추적할 수 있다.

## 알람 설계 가이드
Grafana Alert 또는 Prometheus Alertmanager 구성을 통해 아래 조건을 추천한다.
1. `histogram_quantile(0.95, rate(http_server_requests_seconds_bucket{service="research-service"}[5m])) > 1`
2. `researchex_cache_hit_ratio{cache="researchex::dynamic-query"} < 0.6`
3. `increase(researchex_cache_miss_count_total{service="research-service"}[5m]) > 200`
4. `kafka_consumer_records_lag_max{client_id="research-indexer"} > 1000`

---
이 문서는 학습용 로컬 환경을 전제로 하며, 실제 운영 환경에서는 보안·알람 채널·장애 대응 절차를 추가로 구성해야 한다.

