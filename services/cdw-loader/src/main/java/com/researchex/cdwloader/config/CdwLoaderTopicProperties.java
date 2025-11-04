package com.researchex.cdwloader.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** CDW Loader 서비스가 게시할 Kafka 토픽 이름을 관리한다. */
@ConfigurationProperties(prefix = "app.messaging.topics")
// Lombok을 사용해 토픽 프로퍼티 접근자를 자동 생성한다.
@Getter
@Setter
public class CdwLoaderTopicProperties {

  /** CDW 적재 이벤트를 게시할 Kafka 토픽명. */
  private String cdwLoadEvents;
}
