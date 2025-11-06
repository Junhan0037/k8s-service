package com.researchex.deid.api

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

/** De-identification 서비스의 기본 API로 배포와 연계를 확인할 수 있도록 메타데이터를 제공한다. */
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
