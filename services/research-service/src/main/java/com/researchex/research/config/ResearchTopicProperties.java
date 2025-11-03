package com.researchex.research.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Research 서비스의 Kafka 토픽 설정을 보관한다. */
@ConfigurationProperties(prefix = "app.messaging.topics")
public class ResearchTopicProperties {

  private String deidJobs;

  public String getDeidJobs() {
    return deidJobs;
  }

  public void setDeidJobs(String deidJobs) {
    this.deidJobs = deidJobs;
  }
}
