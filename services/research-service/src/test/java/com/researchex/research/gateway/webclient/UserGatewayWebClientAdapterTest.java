package com.researchex.research.gateway.webclient;

import static org.assertj.core.api.Assertions.assertThat;

import com.researchex.common.gateway.InternalGatewayHeaderProvider;
import com.researchex.common.security.InternalSecurityProperties;
import com.researchex.common.tracing.TracingProperties;
import com.researchex.research.gateway.GatewayClientProperties;
import com.researchex.research.gateway.dto.UserProfileResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

/** WebClient 기반 게이트웨이가 필수 헤더를 전파하는지 검증한다. */
class UserGatewayWebClientAdapterTest {

  private MockWebServer mockWebServer;

  @BeforeEach
  void setUp() throws IOException {
    mockWebServer = new MockWebServer();
    mockWebServer.start();
  }

  @AfterEach
  void tearDown() throws IOException {
    MDC.clear();
    mockWebServer.shutdown();
  }

  @Test
  void fetchUserProfile_shouldPropagateInternalHeaders() throws Exception {
    mockWebServer.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(
                """
                    {
                      "userId": "user-1",
                      "displayName": "Dr. Kim",
                      "primaryRole": "RESEARCHER",
                      "active": true
                    }
                    """));

    GatewayClientProperties properties = new GatewayClientProperties();
    properties.getUser().setBaseUrl(mockWebServer.url("/").toString());
    properties.getUser().setConnectTimeout(Duration.ofSeconds(1));
    properties.getUser().setReadTimeout(Duration.ofSeconds(2));

    TracingProperties tracingProperties = new TracingProperties();
    tracingProperties.setHeaderName("X-Trace-Id");
    InternalSecurityProperties securityProperties = new InternalSecurityProperties();
    securityProperties.setSecret("internal-secret");

    InternalGatewayHeaderProvider headerProvider =
        new InternalGatewayHeaderProvider(tracingProperties, securityProperties);

    InternalGatewayWebClientFactory factory =
        new InternalGatewayWebClientFactory(WebClient.builder(), headerProvider);
    UserGatewayWebClientAdapter adapter = new UserGatewayWebClientAdapter(properties, factory);

    MDC.put("traceId", "trace-webclient-1");

    StepVerifier.create(adapter.fetchUserProfile("user-1"))
        .assertNext(
            response -> {
              assertThat(response.userId()).isEqualTo("user-1");
              assertThat(response.displayName()).isEqualTo("Dr. Kim");
            })
        .verifyComplete();

    RecordedRequest request = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
    assertThat(request).isNotNull();
    assertThat(request.getHeader("X-Trace-Id")).isEqualTo("trace-webclient-1");
    assertThat(request.getHeader("X-Internal-Secret")).isEqualTo("internal-secret");
    assertThat(request.getPath()).isEqualTo("/internal/users/user-1");
  }
}
