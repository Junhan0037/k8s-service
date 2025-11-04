package com.researchex.research;

import com.researchex.research.gateway.GatewayClientProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Research 서비스 애플리케이션의 진입점.
 * Feign 클라이언트 활성화를 통해 내부 통신 게이트웨이 구현(WebClient/Feign)을 유연하게 선택할 수 있도록 구성한다.
 */
@SpringBootApplication
@EnableConfigurationProperties(GatewayClientProperties.class)
@EnableFeignClients(basePackages = "com.researchex.research.gateway.feign")
public class ResearchServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(ResearchServiceApplication.class, args);
  }
}
