package com.researchex.cdwloader.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** CDW Loader 서비스가 게시할 Kafka 토픽 이름을 관리한다. */
@ConfigurationProperties(prefix = "app.messaging.topics")
@Getter
@Setter
public class CdwLoaderTopicProperties {

  /** CDW 적재 이벤트를 게시할 Kafka 토픽명. */
  private String cdwLoadEvents;
}
