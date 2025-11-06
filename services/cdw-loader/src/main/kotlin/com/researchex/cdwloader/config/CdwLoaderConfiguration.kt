package com.researchex.cdwloader.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

/** CDW Loader 서비스 전용 공통 빈을 정의한다. */
@Configuration
@EnableConfigurationProperties(CdwLoaderTopicProperties::class)
class CdwLoaderConfiguration {

    /** I/O 지연이 발생하는 작업을 처리할 전용 스레드 풀. */
    @Bean("cdwLoaderIoExecutor")
    fun cdwLoaderIoExecutor(): Executor {
        return ThreadPoolTaskExecutor().apply {
            threadNamePrefix = "cdw-io-"
            corePoolSize = 4
            maxPoolSize = 8
            setQueueCapacity(200)
            initialize()
        }
    }

    /** 데이터 검증 등 CPU 연산을 처리할 스레드 풀. */
    @Bean("cdwLoaderCpuExecutor")
    fun cdwLoaderCpuExecutor(): Executor {
        return ThreadPoolTaskExecutor().apply {
            threadNamePrefix = "cdw-cpu-"
            corePoolSize = 4
            maxPoolSize = 4
            setQueueCapacity(100)
            initialize()
        }
    }
}
