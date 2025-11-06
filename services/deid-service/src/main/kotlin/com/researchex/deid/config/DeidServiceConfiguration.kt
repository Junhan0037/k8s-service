package com.researchex.deid.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

/** De-identification 서비스 공통 설정(스레드 풀, 구성 프로퍼티 등)을 정의한다. */
@Configuration
@EnableConfigurationProperties(DeidServiceTopicProperties::class)
class DeidServiceConfiguration {

    /** 장기 실행 I/O 작업을 처리할 전용 스레드 풀이다. */
    @Bean("deidIoExecutor")
    fun deidIoExecutor(): Executor {
        return ThreadPoolTaskExecutor().apply {
            threadNamePrefix = "deid-io-"
            corePoolSize = 6
            maxPoolSize = 12
            setQueueCapacity(400)
            initialize()
        }
    }

    /** 데이터 검증/토큰화 등 CPU 집약 작업을 처리할 스레드 풀이다. */
    @Bean("deidCpuExecutor")
    fun deidCpuExecutor(): Executor {
        return ThreadPoolTaskExecutor().apply {
            threadNamePrefix = "deid-cpu-"
            corePoolSize = 4
            maxPoolSize = 4
            setQueueCapacity(200)
            initialize()
        }
    }
}
