package com.researchex.deid.config;

import java.util.concurrent.Executor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** De-identification 서비스 공통 설정(스레드 풀, 구성 프로퍼티 등)을 정의한다. */
@Configuration
@EnableConfigurationProperties(DeidServiceTopicProperties.class)
public class DeidServiceConfiguration {

  /** 장기 실행 I/O 작업을 처리할 전용 스레드 풀. */
  @Bean(name = "deidIoExecutor")
  public Executor deidIoExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("deid-io-");
    executor.setCorePoolSize(6);
    executor.setMaxPoolSize(12);
    executor.setQueueCapacity(400);
    executor.initialize();
    return executor;
  }

  /** 데이터 검증/토큰화 등 CPU 집약 작업을 처리할 스레드 풀. */
  @Bean(name = "deidCpuExecutor")
  public Executor deidCpuExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("deid-cpu-");
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(200);
    executor.initialize();
    return executor;
  }
}
