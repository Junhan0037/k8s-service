#!/bin/sh
# Elasticsearch 초기화 스크립트
# - 인덱스 템플릿을 사전 등록하고 학습용 샘플 데이터를 색인한다.

set -eu

ES_URL="${ELASTICSEARCH_URL:-http://elasticsearch:9200}"
TEMPLATE_DIR="/init/elasticsearch/templates"
DATA_DIR="/init/elasticsearch/data"
MAX_RETRY="${ES_INIT_MAX_RETRY:-30}"
SLEEP_SECONDS="${ES_INIT_RETRY_INTERVAL:-2}"

log() {
  printf '[elasticsearch-init] %s\n' "$1"
}

wait_for_cluster() {
  attempt=1
  while [ "$attempt" -le "$MAX_RETRY" ]; do
    if curl -s "$ES_URL/_cluster/health" | grep -q '"status"'; then
      log "Elasticsearch 클러스터가 준비되었습니다 (시도 ${attempt}/${MAX_RETRY})."
      return 0
    fi
    log "Elasticsearch 응답 대기 중... (시도 ${attempt}/${MAX_RETRY})"
    attempt=$((attempt + 1))
    sleep "$SLEEP_SECONDS"
  done

  log "Elasticsearch 클러스터가 제한 시간 내 준비되지 못했습니다."
  return 1
}

apply_templates() {
  if ! ls "$TEMPLATE_DIR"/*.json >/dev/null 2>&1; then
    log "등록할 인덱스 템플릿이 없습니다."
    return 0
  fi

  for template_file in "$TEMPLATE_DIR"/*.json; do
    template_name=$(basename "$template_file" .json)
    log "인덱스 템플릿 등록: ${template_name}"
    response_file=$(mktemp)
    http_code=$(
      curl -s -o "$response_file" -w "%{http_code}" \
        -X PUT "$ES_URL/_index_template/${template_name}" \
        -H 'Content-Type: application/json' \
        --data-binary @"$template_file"
    )

    if [ "$http_code" -ge 200 ] && [ "$http_code" -lt 300 ]; then
      log "템플릿 ${template_name} 등록 완료."
    else
      log "템플릿 ${template_name} 등록 실패 (HTTP ${http_code}). 응답: $(cat "$response_file")"
      rm -f "$response_file"
      return 1
    fi
    rm -f "$response_file"
  done
}

seed_indices() {
  if ! ls "$DATA_DIR"/*.ndjson >/dev/null 2>&1; then
    log "색인할 샘플 데이터가 없습니다."
    return 0
  fi

  for data_file in "$DATA_DIR"/*.ndjson; do
    index_label=$(basename "$data_file" .ndjson)
    log "샘플 데이터 색인: ${index_label}"
    response_file=$(mktemp)
    http_code=$(
      curl -s -o "$response_file" -w "%{http_code}" \
        -X POST "$ES_URL/_bulk?refresh=true" \
        -H 'Content-Type: application/x-ndjson' \
        --data-binary @"$data_file"
    )

    if [ "$http_code" -ge 200 ] && [ "$http_code" -lt 300 ]; then
      if grep -q '"errors":false' "$response_file"; then
        log "샘플 데이터(${index_label}) 색인 완료."
      else
        log "샘플 데이터(${index_label}) 색인 결과 오류 발생. 응답: $(cat "$response_file")"
        rm -f "$response_file"
        return 1
      fi
    else
      log "샘플 데이터(${index_label}) 색인 실패 (HTTP ${http_code}). 응답: $(cat "$response_file")"
      rm -f "$response_file"
      return 1
    fi
    rm -f "$response_file"
  done
}

main() {
  wait_for_cluster
  apply_templates
  seed_indices
  log "Elasticsearch 초기화 작업이 완료되었습니다."
}

main "$@"
