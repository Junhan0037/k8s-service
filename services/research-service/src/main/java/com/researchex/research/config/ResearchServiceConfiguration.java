package com.researchex.research.config;

import java.util.concurrent.Executor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/** Research 서비스 공통 빈 설정. */
@Configuration
@EnableConfigurationProperties(ResearchTopicProperties.class)
public class ResearchServiceConfiguration {

  /** 검색 문서 가공 등 CPU 중심 작업을 위한 스레드 풀. */
  @Bean(name = "researchCpuExecutor")
  public Executor researchCpuExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("research-cpu-");
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(200);
    executor.initialize();
    return executor;
  }

  /** 인덱스 저장 등 I/O 작업 전용 스레드 풀. */
  @Bean(name = "researchIoExecutor")
  public Executor researchIoExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("research-io-");
    executor.setCorePoolSize(6);
    executor.setMaxPoolSize(12);
    executor.setQueueCapacity(400);
    executor.initialize();
    return executor;
  }

  /** Reactor 파이프라인에서 사용할 CPU 스케줄러. */
  @Bean
  public Scheduler researchCpuScheduler(Executor researchCpuExecutor) {
    return Schedulers.fromExecutor(researchCpuExecutor);
  }

  /** Reactor 파이프라인에서 사용할 I/O 스케줄러. */
  @Bean
  public Scheduler researchIoScheduler(Executor researchIoExecutor) {
    return Schedulers.fromExecutor(researchIoExecutor);
  }
}
