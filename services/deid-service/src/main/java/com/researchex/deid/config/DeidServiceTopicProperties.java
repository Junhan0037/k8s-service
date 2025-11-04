package com.researchex.deid.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** De-identification 서비스에서 사용하는 Kafka 토픽 이름을 정의한다. */
@ConfigurationProperties(prefix = "app.messaging.topics")
// Lombok으로 토픽 속성 접근자를 자동 생성한다.
@Getter
@Setter
public class DeidServiceTopicProperties {

  private String cdwLoadEvents;
  private String deidJobs;
}
