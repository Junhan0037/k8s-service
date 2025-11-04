package com.researchex.research.gateway.feign;

import com.researchex.research.gateway.GatewayClientProperties;
import com.researchex.research.gateway.MedicalRecordGateway;
import com.researchex.research.gateway.dto.MedicalRecordSummaryResponse;
import com.researchex.research.gateway.exception.InternalGatewayException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Feign 기반 의무기록 게이트웨이 구현.
 * 동기 클라이언트 호출을 Reactor {@code boundedElastic} 스케줄러에서 실행해 호출부의 논블로킹 시그니처를 보장한다.
 */
@Component
@ConditionalOnProperty(
    prefix = "researchex.gateway.medical-record",
    name = "client-type",
    havingValue = "FEIGN")
public class MedicalRecordGatewayFeignAdapter implements MedicalRecordGateway {

  private final MedicalRecordGatewayFeignClient client;
  private final GatewayClientProperties.Service properties;

  public MedicalRecordGatewayFeignAdapter(MedicalRecordGatewayFeignClient client, GatewayClientProperties properties) {
    this.client = client;
    this.properties = properties.getMedicalRecord();
  }

  @Override
  public Mono<MedicalRecordSummaryResponse> fetchLatestRecord(String patientId) {
    return Mono.fromCallable(() -> client.fetchLatestRecord(patientId))
        .subscribeOn(Schedulers.boundedElastic())
        .timeout(properties.getReadTimeout())
        .onErrorMap(throwable -> new InternalGatewayException(String.format("의무기록 서비스 Feign 호출 실패 (patientId=%s)", patientId), throwable));
  }
}
