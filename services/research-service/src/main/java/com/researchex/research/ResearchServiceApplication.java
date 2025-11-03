package com.researchex.research;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Research 서비스의 진입점으로 Spring Boot 애플리케이션을 기동한다. 향후 도메인 컴포넌트들이 동일 컨텍스트에 등록되도록 패키지 루트를 기준으로 컴포넌트 스캔
 * 범위를 지정한다.
 */
@SpringBootApplication
public class ResearchServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(ResearchServiceApplication.class, args);
  }
}
