package com.researchex.deid

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/** De-identification 서비스의 애플리케이션 엔트리 포인트다. */
@SpringBootApplication
class DeidServiceApplication

fun main(args: Array<String>) {
    runApplication<DeidServiceApplication>(*args)
}
