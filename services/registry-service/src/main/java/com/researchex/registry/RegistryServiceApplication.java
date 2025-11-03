package com.researchex.registry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Registry 서비스는 임상 데이터 레지스트리를 관리하는 API의 시작점이다. 패키지 루트에 애플리케이션 클래스를 두어 자동 구성 영역을 명확히 유지한다. */
@SpringBootApplication
public class RegistryServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(RegistryServiceApplication.class, args);
  }
}
