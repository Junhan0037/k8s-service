package com.researchex.deid;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** De-identification 서비스의 애플리케이션 엔트리 포인트. */
@SpringBootApplication
public class DeidServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(DeidServiceApplication.class, args);
  }
}
