package com.researchex.deid.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** De-identification 서비스에서 사용하는 Kafka 토픽 이름을 정의한다. */
@ConfigurationProperties(prefix = "app.messaging.topics")
class DeidServiceTopicProperties {
    var cdwLoadEvents: String? = null
    var deidJobs: String? = null
}
