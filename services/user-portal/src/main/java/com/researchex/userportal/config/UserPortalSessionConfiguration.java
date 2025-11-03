package com.researchex.userportal.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.session.data.redis.config.ConfigureRedisAction;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisIndexedHttpSession;

/**
 * User Portal 세션을 Redis에 중앙화하기 위한 설정.
 *
 * 세션 직렬화 포맷을 JSON으로 통일해 서비스 간 확장성을 확보하고, 인덱싱 세션을 활성화해 사용자 기반
 * 세션 조회도 가능하도록 구성한다.
 */
@Configuration
@EnableRedisIndexedHttpSession(maxInactiveIntervalInSeconds = 60 * 30)
public class UserPortalSessionConfiguration {

  /**
   * Spring Session 기본 직렬화기를 JSON 기반으로 교체한다.
   *
   * 직렬화 시점의 타입 정보를 안전하게 보존하면서도 인간이 읽을 수 있는 포맷을 유지해 운영 시 분석이
   * 용이하다.
   */
  @Bean
  public RedisSerializer<Object> springSessionDefaultRedisSerializer(ObjectMapper objectMapper) {
    return new GenericJackson2JsonRedisSerializer(objectMapper);
  }

  /**
   * 일부 Redis 배포 환경에서는 CONFIG 명령이 비활성화되어 있으므로, 세션 초기화 시 해당 명령을 건너뛰도록 설정한다.
   */
  @Bean
  public ConfigureRedisAction configureRedisAction() {
    return ConfigureRedisAction.NO_OP;
  }
}
