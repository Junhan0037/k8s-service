package com.researchex.research.gateway;

import com.researchex.research.gateway.dto.UserProfileResponse;
import reactor.core.publisher.Mono;

/** 사용자 포털 서비스와 통신하기 위한 게이트웨이 인터페이스. */
public interface UserGateway {

  /**
   * 사용자 기본 정보를 조회한다.
   *
   * @param userId 조회 대상 사용자 ID
   * @return 사용자 프로필 데이터를 포함한 Mono
   */
  Mono<UserProfileResponse> fetchUserProfile(String userId);
}
