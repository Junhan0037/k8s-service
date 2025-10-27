import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.configure

plugins {
    // 베이스 플러그인은 루트 프로젝트에서 아티팩트 생성을 방지하고 공통 태스크만 제공한다.
    `base`
    // Spotless는 Java 코드 스타일을 자동 정렬하기 위해 사용한다.
    id("com.diffplug.spotless") version "6.25.0" apply false
    // Spring Dependency Management 플러그인은 BOM 기반 의존성 버전 관리를 돕는다.
    id("io.spring.dependency-management") version "1.1.4" apply false
}

// 전체 프로젝트 수준에서 재사용할 버전 상수를 정의한다.
extra["springBootVersion"] = "3.2.5"
extra["resilience4jVersion"] = "2.2.0"

subprojects {
    // 모든 서브 프로젝트에 공통으로 적용되는 플러그인을 선언한다.
    apply(plugin = "java-library")
    apply(plugin = "checkstyle")
    apply(plugin = "com.diffplug.spotless")

    // JDK 17을 표준으로 사용하여 일관된 빌드 환경을 유지한다.
    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
        withSourcesJar()
        withJavadocJar()
    }

    // 공통 JVM 컴파일 옵션을 지정하여 Character Encoding 이슈를 사전에 제거한다.
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf(
            "-Xlint:unchecked",
            "-Xlint:deprecation"
        ))
    }

    // JUnit Platform을 기본 테스트 런타임으로 사용하여 최신 테스트 생태계를 따른다.
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
        }
    }

    // Checkstyle 설정을 루트 config 디렉터리로 통일한다.
    configure<CheckstyleExtension> {
        toolVersion = "10.12.4"
        configDirectory.set(rootProject.layout.projectDirectory.dir("config/checkstyle"))
        maxWarnings = 0
    }

    // Spotless는 코드 스타일을 자동으로 정리해주는 오픈소스 플러그인
    extensions.configure<SpotlessExtension> {
        java {
            target("src/**/*.java")
            toggleOffOn()
            googleJavaFormat("1.17.0")
            removeUnusedImports()
            trimTrailingWhitespace()
            endWithNewline()
        }
    }

    // 모든 모듈에서 일관된 그룹 및 버전을 유지하기 위한 기본 값이다.
    group = "com.researchex"
    version = "0.1.0-SNAPSHOT"
}

// 플랫폼 모듈들이 동일한 의존성 관리를 참조할 수 있도록 의존성 관리 BOM을 등록한다.
dependencies {
    // 현재 단계에서는 루트 프로젝트에서 직접 컴파일되는 소스가 없으므로 의존성 정의는 생략한다.
}
