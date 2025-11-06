package com.researchex.research.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** Research 서비스의 Kafka 토픽 설정을 보관한다. */
@ConfigurationProperties(prefix = "app.messaging.topics")
class ResearchTopicProperties {
    var deidJobs: String? = null
}
