package com.researchex.deid.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** De-identification 서비스에서 사용하는 Kafka 토픽 이름을 정의한다. */
@ConfigurationProperties(prefix = "app.messaging.topics")
public class DeidServiceTopicProperties {

  private String cdwLoadEvents;
  private String deidJobs;

  public String getCdwLoadEvents() {
    return cdwLoadEvents;
  }

  public void setCdwLoadEvents(String cdwLoadEvents) {
    this.cdwLoadEvents = cdwLoadEvents;
  }

  public String getDeidJobs() {
    return deidJobs;
  }

  public void setDeidJobs(String deidJobs) {
    this.deidJobs = deidJobs;
  }
}
