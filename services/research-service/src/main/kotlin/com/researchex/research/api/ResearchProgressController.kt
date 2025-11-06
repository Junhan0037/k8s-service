package com.researchex.research.api

import com.researchex.research.progress.ResearchProgressEvent
import com.researchex.research.progress.ResearchProgressResponse
import com.researchex.research.progress.ResearchProgressService
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.util.StringUtils
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux

/**
 * 연구 질의 진행률을 SSE로 스트리밍하는 컨트롤러.
 */
@Validated
@RestController
@RequestMapping("/api/research")
class ResearchProgressController(
    private val progressService: ResearchProgressService
) {

    /**
     * 특정 queryId에 대한 진행률 이벤트를 스트리밍한다.
     */
    @GetMapping(path = ["/progress"], produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamProgress(
        @RequestParam("queryId") queryId: String,
        @RequestParam(value = "tenantId", required = false) tenantId: String?
    ): Flux<ServerSentEvent<ResearchProgressResponse>> {
        val normalizedQueryId = queryId.trim()
        val normalizedTenantId = if (StringUtils.hasText(tenantId)) tenantId!!.trim() else "default"
        val stream = progressService.streamByQueryId(normalizedTenantId, normalizedQueryId)
        log.info("연구 진행률 SSE 구독이 요청되었습니다. tenantId={}, queryId={}", normalizedTenantId, normalizedQueryId)
        return stream.map { event -> toSseResponse(event) }
    }

    private fun toSseResponse(event: ResearchProgressEvent): ServerSentEvent<ResearchProgressResponse> {
        val response = ResearchProgressResponse(
            eventId = event.eventId,
            tenantId = event.tenantId,
            queryId = event.queryId,
            status = event.status,
            progressPercentage = event.progressPercentage,
            rowCount = event.rowCount,
            resultLocation = event.resultLocation,
            message = event.message,
            errorCode = event.errorCode,
            errorMessage = event.errorMessage,
            occurredAt = event.occurredAt
        )
        return ServerSentEvent.builder<ResearchProgressResponse>()
            .id(event.eventId)
            .event(event.status.name)
            .data(response)
            .build()
    }

    companion object {
        private val log = LoggerFactory.getLogger(ResearchProgressController::class.java)
    }
}
