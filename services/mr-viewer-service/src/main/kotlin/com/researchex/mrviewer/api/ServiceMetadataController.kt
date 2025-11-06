package com.researchex.mrviewer.api

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

/** MR Viewer 서비스의 메타데이터 엔드포인트를 제공하여 배포 상태를 확인할 수 있게 한다. */
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
