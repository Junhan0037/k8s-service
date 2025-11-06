package com.researchex.mrviewer

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/** 의무기록 뷰어 서비스의 진입점으로 API 서버를 부트스트랩한다. */
@SpringBootApplication
class MrViewerServiceApplication

fun main(args: Array<String>) {
    // runApplication으로 스프링 컨텍스트를 초기화한다.
    runApplication<MrViewerServiceApplication>(*args)
}
