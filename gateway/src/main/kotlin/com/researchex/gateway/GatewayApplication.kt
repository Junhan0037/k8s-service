package com.researchex.gateway

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

/**
 * API Gateway 애플리케이션의 진입점으로, Spring Cloud Gateway 기반 역프록시를 구동한다.
 * 모든 요청은 여기에서 정의한 전역 필터와 라우팅 규칙을 따라 각 마이크로서비스로 위임된다.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
class GatewayApplication

/**
 * Spring Boot 애플리케이션을 부팅한다.
 *
 * @param args 실행 인자
 */
fun main(args: Array<String>) {
    // runApplication을 통해 스프링 컨텍스트를 초기화하고 게이트웨이를 기동한다.
    runApplication<GatewayApplication>(*args)
}
