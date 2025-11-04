package com.researchex.research.gateway.webclient;

import com.researchex.common.gateway.InternalGatewayHeaderProvider;
import com.researchex.research.gateway.GatewayClientProperties;
import io.netty.channel.ChannelOption;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * 내부 통신용 WebClient 인스턴스를 생성하는 헬퍼.
 * 연결/읽기 타임아웃과 공통 헤더 주입 로직을 중앙에서 관리한다.
 */
@Component
public class InternalGatewayWebClientFactory {

  private final WebClient.Builder baseBuilder;
  private final InternalGatewayHeaderProvider headerProvider;

  public InternalGatewayWebClientFactory(WebClient.Builder baseBuilder, InternalGatewayHeaderProvider headerProvider) {
    this.baseBuilder = baseBuilder;
    this.headerProvider = headerProvider;
  }

  /**
   * 주어진 서비스 설정을 기반으로 WebClient 를 생성한다.
   */
  public WebClient create(GatewayClientProperties.Service serviceProperties) {
    int connectTimeoutMillis = Math.toIntExact(Math.min(Integer.MAX_VALUE, serviceProperties.getConnectTimeout().toMillis()));

    HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis)
            .responseTimeout(serviceProperties.getReadTimeout())
            .followRedirect(true);

    return baseBuilder
        .clone()
        .baseUrl(serviceProperties.getBaseUrl())
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .filter(headerPropagationFilter())
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .build();
  }

  private ExchangeFilterFunction headerPropagationFilter() {
    return (request, next) -> {
      ClientRequest mutated = ClientRequest.from(request)
              .headers(headerProvider::enrich)
              .build();
      return next.exchange(mutated);
    };
  }
}
