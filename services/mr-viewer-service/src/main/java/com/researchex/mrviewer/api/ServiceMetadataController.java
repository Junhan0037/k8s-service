package com.researchex.mrviewer.api;

import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** MR Viewer 서비스의 메타데이터 엔드포인트를 제공하여 배포 상태를 확인할 수 있게 한다. */
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
