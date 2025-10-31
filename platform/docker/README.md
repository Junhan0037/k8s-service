# ResearchEx 로컬 인프라 도커 환경

이 디렉터리는 MSA 연구 플랫폼을 로컬에서 구동하기 위한 도커 컴포즈 설정을 포함한다.

## 구성 요소
- `docker-compose.yml`: Kafka, Zookeeper, Redis, PostgreSQL, Elasticsearch, Zipkin, Prometheus, Grafana를 한 번에 기동한다.
- `init/`: Postgres 스키마(`init/postgres/init.sql`), Elasticsearch 템플릿 및 샘플 데이터(`init/elasticsearch`), Kafka 토픽 생성 스크립트(`init/kafka/create-topics.sh`)를 보관한다.
- `prometheus/`: Prometheus 수집 타겟 구성을 정의한다.
- `grafana/`: Grafana 데이터 소스 및 대시보드 프로비저닝 파일을 제공한다.

## 실행 방법
```bash
docker compose -f docker-compose.yml up -d
```

## 종료 방법
```bash
docker compose -f docker-compose.yml down
```

## 데이터 초기화 시 주의 사항
- 컨테이너를 최초 기동할 때 Postgres는 `init/postgres` 이하의 스크립트를 순서대로 실행한다.
- Kafka 토픽 생성은 `kafka-init` 컨테이너가 단 한 번 수행하며, 이미 존재하는 토픽은 유지된다.
