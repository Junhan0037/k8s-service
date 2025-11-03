package com.researchex.research.api;

import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 서비스 기본 상태를 확인하기 위한 공통 템플릿 컨트롤러. 추후 실제 도메인 API가 추가되기 전까지 헬스 체크 외의 간단한 응답을 제공한다. */
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
    // 호출 시점의 기준 시간을 기록하여 API 응답의 신뢰성을 높인다.
    ServiceMetadataResponse payload =
        new ServiceMetadataResponse(serviceName, serviceDescription, OffsetDateTime.now());
    return ResponseEntity.ok(payload);
  }
}
