package com.researchex.cdwloader.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/** CDW Loader 서비스의 메타데이터를 노출하는 기본 컨트롤러. */
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
    ServiceMetadataResponse payload = new ServiceMetadataResponse(serviceName, serviceDescription, OffsetDateTime.now());
    return ResponseEntity.ok(payload);
  }
}
