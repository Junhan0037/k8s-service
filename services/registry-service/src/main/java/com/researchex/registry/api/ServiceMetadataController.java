package com.researchex.registry.api;

import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registry 서비스의 초기 API 스켈레톤으로 운영자가 확인 가능한 메타데이터를 노출한다.
 */
@RestController
@RequestMapping("/api/v1")
public class ServiceMetadataController {

    private final String serviceName;
    private final String serviceDescription;

    public ServiceMetadataController(
            @Value("${spring.application.name}") String serviceName,
            @Value("${service.description}") String serviceDescription) {
        this.serviceName = serviceName;
        this.serviceDescription = serviceDescription;
    }

    @GetMapping("/metadata")
    public ResponseEntity<ServiceMetadataResponse> fetchMetadata() {
        ServiceMetadataResponse payload =
                new ServiceMetadataResponse(serviceName, serviceDescription, OffsetDateTime.now());
        return ResponseEntity.ok(payload);
    }
}
