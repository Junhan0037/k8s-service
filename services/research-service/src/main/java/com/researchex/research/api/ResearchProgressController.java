package com.researchex.research.api;

import com.researchex.research.progress.ResearchProgressEvent;
import com.researchex.research.progress.ResearchProgressResponse;
import com.researchex.research.progress.ResearchProgressService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * 연구 질의 진행률을 SSE로 스트리밍하는 컨트롤러.
 */
@Validated
@RestController
@RequestMapping("/api/research")
public class ResearchProgressController {

    private static final Logger log = LoggerFactory.getLogger(ResearchProgressController.class);

    private final ResearchProgressService progressService;

    public ResearchProgressController(ResearchProgressService progressService) {
        this.progressService = progressService;
    }

    /**
     * 특정 queryId에 대한 진행률 이벤트를 스트리밍한다.
     */
    @GetMapping(path = "/progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ResearchProgressResponse>> streamProgress(
            @RequestParam("queryId") String queryId,
            @RequestParam(value = "tenantId", required = false) String tenantId
    ) {
        String normalizedQueryId = queryId.trim();
        String normalizedTenantId = StringUtils.hasText(tenantId) ? tenantId.trim() : "default";
        Flux<ResearchProgressEvent> stream = progressService.streamByQueryId(normalizedTenantId, normalizedQueryId);
        log.info("연구 진행률 SSE 구독이 요청되었습니다. tenantId={}, queryId={}", normalizedTenantId, normalizedQueryId);

        return stream.map(this::toSseResponse);
    }

    private ServerSentEvent<ResearchProgressResponse> toSseResponse(ResearchProgressEvent event) {
        ResearchProgressResponse response = new ResearchProgressResponse(
                event.eventId(),
                event.tenantId(),
                event.queryId(),
                event.status(),
                event.progressPercentage(),
                event.rowCount(),
                event.resultLocation(),
                event.message(),
                event.errorCode(),
                event.errorMessage(),
                event.occurredAt()
        );
        return ServerSentEvent.<ResearchProgressResponse>builder()
                .id(event.eventId())
                .event(event.status().name())
                .data(response)
                .build();
    }
}
