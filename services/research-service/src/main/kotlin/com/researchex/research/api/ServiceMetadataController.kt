package com.researchex.research.api

import com.researchex.research.application.ServiceMetadataService
import io.micrometer.observation.annotation.Observed
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 서비스 기본 상태를 확인하기 위한 공통 템플릿 컨트롤러. 추후 실제 도메인 API가 추가되기 전까지 헬스 체크 외의 간단한 응답을 제공한다. */
@RestController
@RequestMapping("/api/v1")
class ServiceMetadataController(
    private val metadataService: ServiceMetadataService
) {

    @GetMapping("/metadata")
    @Observed(
        name = "researchex.research.metadata.fetch",
        contextualName = "research-metadata-fetch",
        lowCardinalityKeyValues = ["endpoint", "metadata"]
    )
    fun fetchMetadata(): ResponseEntity<ServiceMetadataResponse> {
        // 메타데이터는 캐시 계층에서 관리되므로 컨트롤러는 단순 위임만 수행한다.
        return ResponseEntity.ok(metadataService.fetchMetadata())
    }
}
