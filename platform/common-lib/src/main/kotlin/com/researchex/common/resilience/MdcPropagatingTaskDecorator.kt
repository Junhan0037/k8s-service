package com.researchex.common.resilience

import org.slf4j.MDC
import org.springframework.core.task.TaskDecorator

/**
 * 비동기 실행 시 MDC 컨텍스트를 함께 전달하는 [TaskDecorator] 구현체다.
 * 워커 스레드에서 남긴 로그와 최초 요청 간 상관관계를 유지하기 위해 필수적이다.
 */
class MdcPropagatingTaskDecorator : TaskDecorator {

    override fun decorate(runnable: Runnable): Runnable {
        val contextMap = MDC.getCopyOfContextMap()
        return Runnable {
            val previous = MDC.getCopyOfContextMap()
            try {
                if (contextMap != null) {
                    MDC.setContextMap(contextMap)
                } else {
                    MDC.clear()
                }
                runnable.run()
            } finally {
                if (previous != null) {
                    MDC.setContextMap(previous)
                } else {
                    MDC.clear()
                }
            }
        }
    }
}
