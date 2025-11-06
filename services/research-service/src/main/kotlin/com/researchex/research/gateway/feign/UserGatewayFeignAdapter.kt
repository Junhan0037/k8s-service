package com.researchex.research.gateway.feign

import com.researchex.research.gateway.GatewayClientProperties
import com.researchex.research.gateway.UserGateway
import com.researchex.research.gateway.dto.UserProfileResponse
import com.researchex.research.gateway.exception.InternalGatewayException
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * Feign 기반 사용자 포털 게이트웨이 구현.
 * Feign 클라이언트는 동기식 호출을 수행하므로, Reactor의 전용 스케줄러를 통해 논블로킹 API 시그니처를 유지한다.
 */
@Component
@ConditionalOnProperty(
    prefix = "researchex.gateway.user",
    name = ["client-type"],
    havingValue = "FEIGN"
)
class UserGatewayFeignAdapter(
    private val client: UserGatewayFeignClient,
    properties: GatewayClientProperties
) : UserGateway {

    private val serviceProperties = properties.user

    override fun fetchUserProfile(userId: String): Mono<UserProfileResponse> {
        return Mono.fromCallable { client.fetchUserProfile(userId) }
            .subscribeOn(Schedulers.boundedElastic())
            .timeout(serviceProperties.readTimeout)
            .onErrorMap { throwable ->
                InternalGatewayException("사용자 포털 Feign 호출 실패 (userId=$userId)", throwable)
            }
    }
}
