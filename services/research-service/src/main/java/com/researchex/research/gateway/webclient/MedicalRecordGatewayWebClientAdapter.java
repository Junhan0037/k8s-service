package com.researchex.research.gateway.webclient;

import com.researchex.research.gateway.GatewayClientProperties;
import com.researchex.research.gateway.MedicalRecordGateway;
import com.researchex.research.gateway.dto.MedicalRecordSummaryResponse;
import com.researchex.research.gateway.exception.InternalGatewayException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * WebClient 기반 의무기록 서비스 게이트웨이 구현.
 *헤더 전파 및 타임아웃 설정을 일관되게 적용해 서비스 간 호출 안정성을 높인다.
 */
@Component
@ConditionalOnProperty(
    prefix = "researchex.gateway.medical-record",
    name = "client-type",
    havingValue = "WEBCLIENT",
    matchIfMissing = true)
public class MedicalRecordGatewayWebClientAdapter implements MedicalRecordGateway {

  private final WebClient client;
  private final GatewayClientProperties.Service properties;

  public MedicalRecordGatewayWebClientAdapter(GatewayClientProperties properties, InternalGatewayWebClientFactory clientFactory) {
    this.properties = properties.getMedicalRecord();
    this.client = clientFactory.create(this.properties);
  }

  @Override
  public Mono<MedicalRecordSummaryResponse> fetchLatestRecord(String patientId) {
    return client
        .get()
        .uri(uriBuilder -> uriBuilder.path("/internal/patients/{patientId}/records/latest").build(patientId))
        .retrieve()
        .bodyToMono(MedicalRecordSummaryResponse.class)
        .timeout(properties.getReadTimeout())
        .onErrorMap(throwable -> new InternalGatewayException(String.format("의무기록 서비스 호출 실패 (patientId=%s)", patientId), throwable));
  }
}
