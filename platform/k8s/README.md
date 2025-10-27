# Minikube 배포 템플릿

> 연구용 MSA 환경을 Minikube에 배포하기 위한 Kustomize 기반 매니페스트 모음입니다. Compose 환경에서 정의된 동일한 컴포넌트를 Kubernetes 리소스로 전환하는 것을 목표로 합니다.

## 디렉터리 구성
- `base/`: 공용 리소스 정의. 인프라(데이터베이스, 메시징), 게이트웨이, 마이크로서비스, 관측성 스택을 포함합니다.
- `overlays/minikube/`: Minikube 환경 특화 설정. 로컬 퍼시스턴트 볼륨 경로, Ingress 클래스, 이미지 태그 등을 오버레이합니다.

## 사용 방법
1. Minikube를 `study.md` 가이드에 따라 기동합니다.
2. `kubectl apply -k platform/k8s/overlays/minikube` 명령으로 전체 스택을 배포합니다.
3. 배포 후 `kubectl get pods -n research-ex`로 정상 기동 여부를 확인합니다.

## 향후 확장
- 퍼블릭 클라우드 배포 시에는 별도의 오버레이 디렉터리를 추가해 노드풀, 스토리지 클래스를 구분합니다.
- Helm Chart 활용 시 현재 구조를 레퍼런스로 삼아 값 파일(`values.yaml`)을 정리합니다.
