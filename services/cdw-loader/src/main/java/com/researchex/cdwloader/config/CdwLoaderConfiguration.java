package com.researchex.cdwloader.config;

import java.util.concurrent.Executor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** CDW Loader 서비스 전용 공통 빈을 정의한다. */
@Configuration
@EnableConfigurationProperties(CdwLoaderTopicProperties.class)
public class CdwLoaderConfiguration {

  /** I/O 지연이 발생하는 작업을 처리할 전용 스레드 풀. */
  @Bean(name = "cdwLoaderIoExecutor")
  public Executor cdwLoaderIoExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("cdw-io-");
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(8);
    executor.setQueueCapacity(200);
    executor.initialize();
    return executor;
  }

  /** 데이터 검증 등 CPU 연산을 처리할 스레드 풀. */
  @Bean(name = "cdwLoaderCpuExecutor")
  public Executor cdwLoaderCpuExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("cdw-cpu-");
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(100);
    executor.initialize();
    return executor;
  }
}
