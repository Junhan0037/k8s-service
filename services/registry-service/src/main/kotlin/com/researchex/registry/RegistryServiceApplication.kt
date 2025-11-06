package com.researchex.registry

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/** Registry 서비스는 임상 데이터 레지스트리를 관리하는 API의 시작점이다. */
@SpringBootApplication
class RegistryServiceApplication

fun main(args: Array<String>) {
    runApplication<RegistryServiceApplication>(*args)
}
