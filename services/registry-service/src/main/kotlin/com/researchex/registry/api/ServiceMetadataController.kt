package com.researchex.registry.api

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

/** Registry 서비스의 초기 API 스켈레톤으로 운영자가 확인 가능한 메타데이터를 노출한다. */
@RestController
@RequestMapping("/api/v1")
class ServiceMetadataController(
    @Value("\${spring.application.name}") private val serviceName: String,
    @Value("\${service.description}") private val serviceDescription: String,
) {

    @GetMapping("/metadata")
    fun fetchMetadata(): ResponseEntity<ServiceMetadataResponse> {
        val payload = ServiceMetadataResponse(serviceName, serviceDescription, OffsetDateTime.now())
        return ResponseEntity.ok(payload)
    }
}
