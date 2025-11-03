package com.researchex.cdwloader;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** CDW Loader 서비스의 진입점. 배치/스트리밍 파이프라인의 기동을 담당한다. */
@SpringBootApplication
public class CdwLoaderApplication {

  public static void main(String[] args) {
    SpringApplication.run(CdwLoaderApplication.class, args);
  }
}
