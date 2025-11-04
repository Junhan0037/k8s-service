package com.researchex.research.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Research 서비스의 Kafka 토픽 설정을 보관한다. */
@ConfigurationProperties(prefix = "app.messaging.topics")
// Lombok을 사용해 토픽 속성 접근자를 자동으로 생성한다.
@Getter
@Setter
public class ResearchTopicProperties {

  private String deidJobs;
}
