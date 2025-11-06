package com.researchex.research.gateway.webclient

import com.researchex.research.gateway.GatewayClientProperties
import com.researchex.research.gateway.UserGateway
import com.researchex.research.gateway.dto.UserProfileResponse
import com.researchex.research.gateway.exception.InternalGatewayException
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

/**
 * WebClient 기반 사용자 포털 게이트웨이 구현.
 * TraceId 및 내부 시크릿 헤더는 [InternalGatewayWebClientFactory] 단계에서 자동으로 주입된다.
 */
@Component
@ConditionalOnProperty(
    prefix = "researchex.gateway.user",
    name = ["client-type"],
    havingValue = "WEBCLIENT",
    matchIfMissing = true
)
class UserGatewayWebClientAdapter(
    properties: GatewayClientProperties,
    clientFactory: InternalGatewayWebClientFactory
) : UserGateway {

    private val serviceProperties = properties.user
    private val client: WebClient = clientFactory.create(serviceProperties)

    override fun fetchUserProfile(userId: String): Mono<UserProfileResponse> {
        return client.get()
            .uri { builder -> builder.path("/internal/users/{userId}").build(userId) }
            .retrieve()
            .bodyToMono(UserProfileResponse::class.java)
            .timeout(serviceProperties.readTimeout)
            .onErrorMap { throwable ->
                InternalGatewayException("사용자 포털 호출 실패 (userId=$userId)", throwable)
            }
    }
}
