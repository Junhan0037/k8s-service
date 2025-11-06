package com.researchex.cdwloader

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/** CDW Loader 서비스의 진입점으로 배치/스트리밍 파이프라인의 기동을 담당한다. */
@SpringBootApplication
class CdwLoaderApplication

fun main(args: Array<String>) {
    runApplication<CdwLoaderApplication>(*args)
}
