package com.researchex.research.gateway.feign;

import com.researchex.common.gateway.InternalGatewayHeaderProvider;
import com.researchex.research.gateway.GatewayClientProperties;
import feign.Request;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

/** 의무기록 뷰어 서비스용 Feign 설정. */
@Configuration
public class MedicalRecordGatewayFeignConfiguration {

  private final GatewayClientProperties properties;

  public MedicalRecordGatewayFeignConfiguration(GatewayClientProperties properties) {
    this.properties = properties;
  }

  @Bean
  public RequestInterceptor medicalRecordHeaderInterceptor(InternalGatewayHeaderProvider headerProvider) {
    return template -> {
      HttpHeaders headers = new HttpHeaders();
      headerProvider.enrich(headers);
      headers.forEach((name, values) -> values.forEach(value -> template.header(name, value)));
    };
  }

  @Bean
  public Request.Options medicalRecordRequestOptions() {
    GatewayClientProperties.Service service = properties.getMedicalRecord();
    return new Request.Options(service.getConnectTimeout(), service.getReadTimeout(), true);
  }
}
