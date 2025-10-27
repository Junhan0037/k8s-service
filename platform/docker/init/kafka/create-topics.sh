#!/bin/bash
# ResearchEx 로컬 개발용 Kafka 토픽을 초기화하는 스크립트
# - docker-compose 기동 시 단 한 번 실행되며, 이미 존재하는 토픽은 그대로 둔다.
# - Confluent Kafka 이미지에 포함된 기본 CLI(kafka-topics)를 사용한다.

set -euo pipefail

BOOTSTRAP_SERVERS="${BOOTSTRAP_SERVERS:-kafka:9092}"
KAFKA_TOPICS_BIN="${KAFKA_TOPICS_BIN:-kafka-topics}" # 이미지마다 설치 경로가 다르므로 래퍼 변수로 명시한다.

kafka_topics() {
  "${KAFKA_TOPICS_BIN}" "$@"
}

echo "[kafka-init] Kafka 브로커(${BOOTSTRAP_SERVERS}) 준비 여부를 확인합니다."
for attempt in {1..30}; do
  if kafka_topics --bootstrap-server "${BOOTSTRAP_SERVERS}" --list >/dev/null 2>&1; then
    echo "[kafka-init] Kafka 브로커 연결에 성공했습니다."
    break
  fi
  echo "[kafka-init] Kafka 브로커가 아직 준비되지 않았습니다. ${attempt}/30 회 재시도합니다."
  sleep 5
done

if ! kafka_topics --bootstrap-server "${BOOTSTRAP_SERVERS}" --list >/dev/null 2>&1; then
  echo "[kafka-init] Kafka 브로커에 연결할 수 없어 토픽 생성을 중단합니다." >&2
  exit 1
fi

# 이름:파티션:복제수 형식으로 토픽 스펙을 정의한다.
declare -a TOPIC_SPECS=(
  "research.query.request:3:1"
  "research.query.result:3:1"
  "deid.jobs:3:1"
  "cdw.load.events:3:1"
  "researchex.dlq:3:1"
)

for spec in "${TOPIC_SPECS[@]}"; do
  IFS=":" read -r topic partitions replication <<<"${spec}"
  if kafka_topics --bootstrap-server "${BOOTSTRAP_SERVERS}" --describe --topic "${topic}" >/dev/null 2>&1; then
    echo "[kafka-init] 이미 존재하는 토픽을 건너뜁니다: ${topic}"
    continue
  fi

  echo "[kafka-init] 토픽을 생성합니다: ${topic} (partitions=${partitions}, replication=${replication})"
  kafka_topics \
    --bootstrap-server "${BOOTSTRAP_SERVERS}" \
    --create \
    --topic "${topic}" \
    --partitions "${partitions}" \
    --replication-factor "${replication}"
done

echo "[kafka-init] Kafka 토픽 초기화가 완료되었습니다."
