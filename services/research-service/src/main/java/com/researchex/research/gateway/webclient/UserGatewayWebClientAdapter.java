package com.researchex.research.gateway.webclient;

import com.researchex.research.gateway.GatewayClientProperties;
import com.researchex.research.gateway.UserGateway;
import com.researchex.research.gateway.dto.UserProfileResponse;
import com.researchex.research.gateway.exception.InternalGatewayException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * WebClient 기반 사용자 포털 게이트웨이 구현.
 * TraceId 및 내부 시크릿 헤더는 {@link InternalGatewayWebClientFactory} 단계에서 자동으로 주입된다.
 */
@Component
@ConditionalOnProperty(
    prefix = "researchex.gateway.user",
    name = "client-type",
    havingValue = "WEBCLIENT",
    matchIfMissing = true)
public class UserGatewayWebClientAdapter implements UserGateway {

  private final WebClient client;
  private final GatewayClientProperties.Service properties;

  public UserGatewayWebClientAdapter(GatewayClientProperties properties, InternalGatewayWebClientFactory clientFactory) {
    this.properties = properties.getUser();
    this.client = clientFactory.create(this.properties);
  }

  @Override
  public Mono<UserProfileResponse> fetchUserProfile(String userId) {
    return client
        .get()
        .uri(uriBuilder -> uriBuilder.path("/internal/users/{userId}").build(userId))
        .retrieve()
        .bodyToMono(UserProfileResponse.class)
        .timeout(properties.getReadTimeout())
        .onErrorMap(throwable -> new InternalGatewayException(String.format("사용자 포털 호출 실패 (userId=%s)", userId), throwable));
  }
}
