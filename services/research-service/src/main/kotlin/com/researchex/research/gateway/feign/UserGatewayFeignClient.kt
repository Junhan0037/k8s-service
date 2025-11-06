package com.researchex.research.gateway.feign

import com.researchex.research.gateway.dto.UserProfileResponse
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

/**
 * 사용자 포털 서비스에 대한 Feign 클라이언트 정의.
 * URL 및 타임아웃은 [UserGatewayFeignConfiguration] 에서 주입한 프로퍼티를 따른다.
 */
@FeignClient(
    name = "userGatewayClient",
    url = "\${researchex.gateway.user.base-url}",
    configuration = [UserGatewayFeignConfiguration::class]
)
interface UserGatewayFeignClient {

    @GetMapping("/internal/users/{userId}")
    fun fetchUserProfile(@PathVariable("userId") userId: String): UserProfileResponse
}
