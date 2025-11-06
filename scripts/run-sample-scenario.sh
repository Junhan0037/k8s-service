#!/usr/bin/env bash
# ResearchEx 로컬 환경에서 Step 16 샘플 시나리오(적재→가명화→색인→검색)를 실행한다.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
DATA_DIR="${ROOT_DIR}/samples/scenarios"
PAYLOAD_FILE="${DATA_DIR}/cdw-batch-request.json"
SEARCH_FILE="${DATA_DIR}/research-search-request.json"

# 필수 도구 확인: curl과 jq는 시나리오 검증에 반드시 필요하다.
for cmd in curl jq; do
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    echo "[ERROR] '${cmd}' 명령을 찾을 수 없습니다. 설치 후 다시 실행해주세요." >&2
    exit 1
  fi
done

# 샘플 데이터 파일 존재 여부를 선행 검증해 사용자 실수를 빠르게 피드백한다.
for file in "${PAYLOAD_FILE}" "${SEARCH_FILE}"; do
  if [ ! -f "${file}" ]; then
    echo "[ERROR] 샘플 데이터 파일을 찾을 수 없습니다: ${file}" >&2
    exit 1
  fi
done

# 서비스 엔드포인트는 환경 변수로 재정의할 수 있으며, 기본값은 로컬 포트에 맞춘다.
CDW_BASE_URL="${CDW_BASE_URL:-http://localhost:8106}"
RESEARCH_BASE_URL="${RESEARCH_BASE_URL:-http://localhost:8101}"
POLL_INTERVAL_SECONDS="${SCENARIO_POLL_INTERVAL:-2}"
MAX_POLL_ATTEMPTS="${SCENARIO_MAX_ATTEMPTS:-15}"
INTERNAL_SECRET_HEADER="${RESEARCHEX_INTERNAL_SECRET_HEADER:-X-Internal-Secret}"
INTERNAL_SECRET_VALUE="${RESEARCHEX_INTERNAL_SECRET:-researchex-internal-secret}"

# jq를 이용해 시나리오 파라미터를 추출한다.
tenant_id="$(jq -r '.tenantId' "${PAYLOAD_FILE}")"
batch_id="$(jq -r '.batchId' "${PAYLOAD_FILE}")"
source_system="$(jq -r '.sourceSystem' "${PAYLOAD_FILE}")"
record_count="$(jq -r '.recordCount' "${PAYLOAD_FILE}")"
search_query="$(jq -r '.query' "${SEARCH_FILE}")"
search_page="$(jq -r '.page' "${SEARCH_FILE}")"
search_size="$(jq -r '.size' "${SEARCH_FILE}")"
search_use_cache="$(jq -r '.useCache' "${SEARCH_FILE}")"
scenario_query_id="scenario-$(date +%s)"

# GET 파라미터 배열을 구성한다. --data-urlencode 옵션을 활용해 특수 문자를 안전하게 전송한다.
# macOS 기본 Bash(3.x) 환경과의 호환성을 위해 임시 파일을 활용해 배열을 구성한다.
filter_params=()
filter_tmp="$(mktemp)"
jq -r '.filters[]?' "${SEARCH_FILE}" > "${filter_tmp}"
while IFS= read -r filter_value; do
  [ -n "${filter_value}" ] && filter_params+=("${filter_value}")
done < "${filter_tmp}"
rm -f "${filter_tmp}"

sort_params=()
sort_tmp="$(mktemp)"
jq -r '.sort[]?' "${SEARCH_FILE}" > "${sort_tmp}"
while IFS= read -r sort_value; do
  [ -n "${sort_value}" ] && sort_params+=("${sort_value}")
done < "${sort_tmp}"
rm -f "${sort_tmp}"

log() {
  local level="$1"
  local message="$2"
  printf '[%-5s] %s\n' "${level}" "${message}"
}

post_cdw_batch() {
  log "STEP" "CDW Loader 배치 적재 요청을 전송합니다. tenantId=${tenant_id}, batchId=${batch_id}, recordCount=${record_count}"
  local response_file
  response_file="$(mktemp)"
  local http_code
  http_code="$(
    curl -sS -o "${response_file}" -w "%{http_code}" \
      -H 'Content-Type: application/json' \
      --data @"${PAYLOAD_FILE}" \
      "${CDW_BASE_URL}/api/cdw/batches"
  )"

  if [ "${http_code}" -ne 202 ]; then
    log "ERROR" "CDW Loader 요청이 실패했습니다 (HTTP ${http_code}). 응답 본문을 확인하세요."
    cat "${response_file}" >&2
    rm -f "${response_file}"
    exit 1
  fi

  log "INFO" "배치 적재 요청이 수락되었습니다. 비동기 파이프라인이 시작되었습니다."
  cat "${response_file}"
  rm -f "${response_file}"
}

poll_research_index() {
  log "STEP" "Research 서비스 인덱스에서 가명화 결과가 반영될 때까지 폴링합니다."
  local attempt=1
  while [ "${attempt}" -le "${MAX_POLL_ATTEMPTS}" ]; do
    local response
    if ! response="$(curl -sS -H "${INTERNAL_SECRET_HEADER}: ${INTERNAL_SECRET_VALUE}" "${RESEARCH_BASE_URL}/internal/research/index")"; then
      log "WARN" "인덱스 조회에 실패했습니다. 재시도합니다. attempt=${attempt}"
      attempt=$((attempt + 1))
      sleep "${POLL_INTERVAL_SECONDS}"
      continue
    fi

    local matched_doc
    matched_doc="$(echo "${response}" | jq -c --arg batch "${batch_id}" 'map(select(.jobId | contains($batch))) | first // empty')"
    if [ -n "${matched_doc}" ]; then
      log "INFO" "연구 인덱스에서 신규 문서를 확인했습니다."
      echo "${matched_doc}" | jq
      return 0
    fi

    log "INFO" "아직 인덱스에 문서가 없습니다. attempt=${attempt}/${MAX_POLL_ATTEMPTS}"
    attempt=$((attempt + 1))
    sleep "${POLL_INTERVAL_SECONDS}"
  done

  log "ERROR" "정해진 시간 내에 가명화 결과를 찾지 못했습니다. Kafka 파이프라인과 서비스 상태를 확인하세요."
  exit 1
}

invoke_research_search() {
  log "STEP" "Research 검색 API를 호출합니다. query='${search_query}'"
  local curl_args=(
    -sS
    -G
    "${RESEARCH_BASE_URL}/api/research/search"
    --data-urlencode "tenantId=${tenant_id}"
    --data-urlencode "query=${search_query}"
    --data-urlencode "page=${search_page}"
    --data-urlencode "size=${search_size}"
    --data-urlencode "cache=${search_use_cache}"
    --data-urlencode "queryId=${scenario_query_id}"
  )

  for filter in "${filter_params[@]}"; do
    curl_args+=(--data-urlencode "filter=${filter}")
  done

  for sort in "${sort_params[@]}"; do
    curl_args+=(--data-urlencode "sort=${sort}")
  done

  local response_file
  response_file="$(mktemp)"
  local http_code
  http_code="$(curl "${curl_args[@]}" -o "${response_file}" -w "%{http_code}")"

  if [ "${http_code}" -ne 200 ]; then
    log "ERROR" "연구 검색 API 호출이 실패했습니다 (HTTP ${http_code}). 응답 본문을 확인하세요."
    cat "${response_file}" >&2
    rm -f "${response_file}"
    exit 1
  fi

  local hit_count
  hit_count="$(jq '.items | length' "${response_file}")"
  local top_title
  top_title="$(jq -r '.items[0].title // "N/A"' "${response_file}")"

  log "INFO" "검색 성공: 총 ${hit_count}건, 첫 번째 연구 제목='${top_title}'"
  jq '{status, pagination, items}' "${response_file}"
  rm -f "${response_file}"
}

main() {
  log "INFO" "샘플 시나리오를 시작합니다. sourceSystem=${source_system}"
  post_cdw_batch
  poll_research_index
  invoke_research_search
  log "DONE" "샘플 시나리오가 완료되었습니다. Zipkin 및 Grafana에서 트레이스/메트릭을 확인해보세요."
}

main "$@"
