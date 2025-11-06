package com.researchex.research.gateway.exception

/**
 * 내부 서비스 게이트웨이 호출 실패 시 throw 되는 런타임 예외다.
 * 추후 회복탄력성 패턴과 연계해 재시도/폴백 로직을 적용할 때 공통 예외 타입으로 활용한다.
 */
class InternalGatewayException(message: String, cause: Throwable) : RuntimeException(message, cause)
