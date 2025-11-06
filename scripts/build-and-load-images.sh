#!/usr/bin/env bash
# Spring Boot 서비스 JAR을 빌드한 뒤 Docker 이미지를 생성하고 Minikube 노드에 적재한다.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Kubernetes 매니페스트와 동일한 이미지 이름을 유지하기 위해 서비스 모듈명을 그대로 사용한다.
SERVICES=(
  "cdw-loader"
  "deid-service"
  "mr-viewer-service"
  "registry-service"
  "research-service"
  "user-portal"
)

log() {
  local level="$1"
  shift
  printf '[%-5s] %s\n' "${level}" "$*"
}

detect_platform() {
  local host_arch
  host_arch="$(uname -m)"
  case "${host_arch}" in
    arm64 | aarch64)
      TARGET_PLATFORM="${TARGET_PLATFORM:-linux/arm64}"
      ;;
    x86_64 | amd64)
      TARGET_PLATFORM="${TARGET_PLATFORM:-linux/amd64}"
      ;;
    *)
      log "WARN" "알 수 없는 호스트 아키텍처(${host_arch})입니다. 기본값 linux/amd64 를 사용합니다."
      TARGET_PLATFORM="${TARGET_PLATFORM:-linux/amd64}"
      ;;
  esac
  log "INFO" "Docker 빌드 타겟 플랫폼을 ${TARGET_PLATFORM} 로 설정했습니다."
}

prepare_gradle_cache() {
  # 샌드박스 환경에서는 홈 디렉터리 접근이 제한될 수 있으므로 프로젝트 내부로 Gradle 캐시 경로를 고정한다.
  export GRADLE_USER_HOME="${PROJECT_ROOT}/.gradle-cache"
  mkdir -p "${GRADLE_USER_HOME}"
  log "INFO" "Gradle 캐시 경로를 ${GRADLE_USER_HOME} 로 지정했습니다."
}

build_boot_jars() {
  log "INFO" "모든 서비스 모듈의 Spring Boot JAR을 빌드합니다."
  local gradle_tasks=()
  for service in "${SERVICES[@]}"; do
    gradle_tasks+=(":services:${service}:bootJar")
  done
  ./gradlew "${gradle_tasks[@]}"
}

build_service_image() {
  local service="$1"
  local image_tag="researchex/${service}:latest"

  # Boot Jar 파일 경로를 탐색한다. (sources/javadoc JAR은 제외한다.)
  local jar_path
  jar_path="$(find "${PROJECT_ROOT}/services/${service}/build/libs" -maxdepth 1 -type f -name '*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' | head -n 1)"
  if [[ -z "${jar_path:-}" ]]; then
    log "ERROR" "${service} 모듈의 Boot Jar를 찾을 수 없습니다."
    return 1
  fi

  # Docker 컨텍스트는 프로젝트 루트이므로 상대 경로로 변환한다.
  local jar_relative_path
  jar_relative_path="${jar_path#"${PROJECT_ROOT}/"}"
  if [[ "${jar_relative_path}" == "${jar_path}" ]]; then
    log "WARN" "JAR 상대 경로 계산에 실패했습니다. 절대 경로를 사용하지 못해 빌드가 중단됩니다."
    return 1
  fi

  log "STEP" "${service} Docker 이미지를 빌드합니다. (JAR=${jar_relative_path})"
  docker build \
    --platform "${TARGET_PLATFORM}" \
    --build-arg "SERVICE_NAME=${service}" \
    --build-arg "JAR_PATH=${jar_relative_path}" \
    -t "${image_tag}" \
    -f "${PROJECT_ROOT}/docker/service.Dockerfile" \
    "${PROJECT_ROOT}"
}

load_images_to_minikube() {
  if command -v minikube >/dev/null 2>&1; then
    if minikube status >/dev/null 2>&1; then
      log "INFO" "Minikube 노드로 이미지를 로드합니다."
      for service in "${SERVICES[@]}"; do
        local_image="researchex/${service}:latest"
        log "STEP" "minikube image load ${local_image}"
        minikube image load "${local_image}"
      done
      log "INFO" "이미지 로드가 완료되었습니다. 'kubectl rollout restart'로 재배포하세요."
    else
      log "WARN" "Minikube가 실행 중이 아닙니다. 클러스터 기동 후 'minikube image load researchex/<서비스>:latest'를 직접 수행하세요."
    fi
  else
    log "WARN" "minikube 명령을 찾을 수 없습니다. 로컬 Docker 환경에서 이미지 빌드는 완료됐습니다."
  fi
}

main() {
  log "INFO" "Spring Boot 서비스 이미지를 빌드하고 Minikube에 적재합니다."
  detect_platform
  prepare_gradle_cache
  pushd "${PROJECT_ROOT}" >/dev/null
  build_boot_jars
  for service in "${SERVICES[@]}"; do
    build_service_image "${service}"
  done
  popd >/dev/null
  load_images_to_minikube
}

main "$@"
