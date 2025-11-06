# Minikube 배포 템플릿

> 연구용 MSA 환경을 Minikube에 배포하기 위한 Kustomize 기반 매니페스트 모음입니다. Compose 환경에서 정의된 동일한 컴포넌트를 Kubernetes 리소스로 전환하는 것을 목표로 합니다.

## 디렉터리 구성
- `base/`: 공용 리소스 정의. 인프라(데이터베이스, 메시징), 게이트웨이, 마이크로서비스, 관측성 스택을 포함합니다.
- `overlays/minikube/`: Minikube 환경 특화 설정. 로컬 퍼시스턴트 볼륨 경로, Ingress 클래스, 이미지 태그 등을 오버레이합니다.

## macOS (Apple Silicon) Minikube 환경 구성 절차
1. 설치 확인: `brew install minikube kubectl`로 최신 버전을 설치하고 `minikube version`, `kubectl version --client`로 정상 설치했는지 점검합니다.
2. Docker Desktop 준비: Apple Silicon(M1/M2)에서는 기본적으로 Docker Desktop이 필요하므로 ARM 네이티브 버전이 설치돼 있고 로그인·실행 상태인지 확인합니다.
3. 드라이버 설정: Docker Desktop이 준비됐다면 `minikube config set driver docker`로 기본 드라이버를 등록합니다. 기존 Minikube 클러스터가 있다면 "These changes will take effect upon a minikube delete and then a minikube start" 안내가 출력되므로, 새 구성을 적용하려면 `minikube delete` 후 재기동하거나 `minikube start --driver=docker ...`처럼 명령행에서 드라이버를 직접 지정합니다. 또한 Docker Desktop Preferences에서 CPU/메모리를 실습 요구량(예: 4 vCPU, 8GB) 이상으로 설정해 리소스 충돌을 피합니다.
4. 클러스터 기동: `minikube start --driver=docker --cpus 4 --memory 6144 --kubernetes-version stable`과 같이 ARM 아키텍처에 맞는 자원 파라미터를 지정해 기동하고 `kubectl get nodes`, `kubectl cluster-info`로 노드가 `Ready` 상태인지 확인합니다.
5. 컨텍스트 정리: 컨텍스트가 자동으로 `minikube`로 잡히지 않았다면 `kubectl config use-context minikube`를 실행하고 `kubectl get ns`로 연결이 정상인지 확인합니다.
6. 애드온 설정: `minikube addons list`로 사용 가능한 애드온을 확인하고, 필요 기능만 `minikube addons enable <애드온명>`으로 활성화합니다. Apple Silicon 환경에서는 불필요한 애드온을 최소화해 자원 사용량을 관리합니다.
7. 이미지 싱크: Docker Desktop에서 미리 받아 둔 자체 빌드 이미지가 있다면 `minikube image load researchex/<이미지명>:<태그>` 형태로 Minikube 노드에 로드하거나 `eval "$(minikube docker-env)"` 후 `docker pull`/`docker build`를 실행해 동일한 이미지가 Minikube 내부 레지스트리에 존재하도록 맞춥니다.
8. 검증 배포: 간단한 테스트 매니페스트를 작성해 `kubectl apply -f <파일명>`으로 배포한 뒤 `kubectl get pods,svc -n default`로 상태를 점검하고 `minikube service <서비스명>`으로 로컬 접근을 검증합니다.
9. 종료 및 초기화: 실습이 끝나면 `minikube stop`으로 클러스터를 중지하고, 필요 시 `minikube delete`로 완전히 삭제해 다음 실습을 위한 초기 상태를 유지합니다.

## 사용 방법
1. Minikube를 `study.md` 가이드에 따라 기동합니다.
2. `scripts/build-and-load-images.sh` 스크립트를 실행해 Spring Boot 서비스 이미지를 빌드하고 Minikube 노드로 푸시합니다.
3. `kubectl apply -k platform/k8s/overlays/minikube` 명령으로 전체 스택을 배포합니다.
4. 배포 후 `kubectl get pods -n research-ex`로 정상 기동 여부를 확인합니다.

## 향후 확장
- 퍼블릭 클라우드 배포 시에는 별도의 오버레이 디렉터리를 추가해 노드풀, 스토리지 클래스를 구분합니다.
- Helm Chart 활용 시 현재 구조를 레퍼런스로 삼아 값 파일(`values.yaml`)을 정리합니다.
