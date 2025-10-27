rootProject.name = "msa-researchex"

// 모듈 구조를 명확히 하기 위해 필요한 프로젝트들을 모두 include 한다.
include("gateway")
include("platform:common-lib")
include("services:research-service")
include("services:registry-service")
include("services:mr-viewer-service")
include("services:deid-service")
include("services:user-portal")
include("services:cdw-loader")

// Gradle 버전 카탈로그 사용 시 안정적인 저장소 구성을 위해 명시적으로 선언한다.
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}
