package com.researchex.research.gateway.feign;

import com.researchex.common.gateway.InternalGatewayHeaderProvider;
import com.researchex.research.gateway.GatewayClientProperties;
import feign.Request;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

/** 사용자 게이트웨이 Feign 클라이언트에서 사용할 설정 모음. */
@Configuration
public class UserGatewayFeignConfiguration {

  private final GatewayClientProperties properties;

  public UserGatewayFeignConfiguration(GatewayClientProperties properties) {
    this.properties = properties;
  }

  @Bean
  public RequestInterceptor userGatewayHeaderInterceptor(
      InternalGatewayHeaderProvider headerProvider) {
    return template -> {
      HttpHeaders headers = new HttpHeaders();
      headerProvider.enrich(headers);
      headers.forEach((name, values) -> values.forEach(value -> template.header(name, value)));
    };
  }

  @Bean
  public Request.Options userGatewayRequestOptions() {
    GatewayClientProperties.Service service = properties.getUser();
    return new Request.Options(service.getConnectTimeout(), service.getReadTimeout(), true);
  }
}
