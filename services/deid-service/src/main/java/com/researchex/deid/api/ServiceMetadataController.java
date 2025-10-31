package com.researchex.deid.api;

import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * De-identification 서비스의 기본 API로 배포와 연계를 확인할 수 있도록 메타데이터를 제공한다.
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
