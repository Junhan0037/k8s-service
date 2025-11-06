package com.researchex.research.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import reactor.core.scheduler.Scheduler
import reactor.core.scheduler.Schedulers
import java.util.concurrent.Executor

/** Research 서비스 공통 빈 설정이다. */
@Configuration
@EnableConfigurationProperties(ResearchTopicProperties::class)
class ResearchServiceConfiguration {

    /** 검색 문서 가공 등 CPU 중심 작업을 위한 스레드 풀. */
    @Bean("researchCpuExecutor")
    fun researchCpuExecutor(): Executor {
        return ThreadPoolTaskExecutor().apply {
            threadNamePrefix = "research-cpu-"
            corePoolSize = 4
            maxPoolSize = 4
            setQueueCapacity(200)
            initialize()
        }
    }

    /** 인덱스 저장 등 I/O 작업 전용 스레드 풀. */
    @Bean("researchIoExecutor")
    fun researchIoExecutor(): Executor {
        return ThreadPoolTaskExecutor().apply {
            threadNamePrefix = "research-io-"
            corePoolSize = 6
            maxPoolSize = 12
            setQueueCapacity(400)
            initialize()
        }
    }

    /** Reactor 파이프라인에서 사용할 CPU 스케줄러. */
    @Bean
    fun researchCpuScheduler(researchCpuExecutor: Executor): Scheduler {
        return Schedulers.fromExecutor(researchCpuExecutor)
    }

    /** Reactor 파이프라인에서 사용할 I/O 스케줄러. */
    @Bean
    fun researchIoScheduler(researchIoExecutor: Executor): Scheduler {
        return Schedulers.fromExecutor(researchIoExecutor)
    }
}
