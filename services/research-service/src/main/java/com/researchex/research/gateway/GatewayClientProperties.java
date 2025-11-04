package com.researchex.research.gateway;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

/**
 * 내부 통신 게이트웨이 설정을 외부 프로퍼티에서 주입받기 위한 설정 클래스.
 * 서비스별 베이스 URL 및 타임아웃, 구현체 종류(WebClient/Feign)를 유연하게 전환할 수 있도록 한다.
 */
@Validated
@ConfigurationProperties(prefix = "researchex.gateway")
@Getter
public class GatewayClientProperties {

  @NestedConfigurationProperty private final Service user = new Service();
  @NestedConfigurationProperty private final Service medicalRecord = new Service();

  /** 개별 내부 서비스 호출에 필요한 기본 속성. */
  @Getter
  @Setter
  public static class Service {

    /** 호출 대상 서비스의 베이스 URL (예: http://localhost:8200). */
    @NotBlank private String baseUrl = "http://localhost";

    /** 연결 타임아웃. */
    private Duration connectTimeout = Duration.ofSeconds(2);

    /** 읽기 타임아웃. */
    private Duration readTimeout = Duration.ofSeconds(5);

    /** 사용할 게이트웨이 구현체(WebClient/Feign). */
    private GatewayClientType clientType = GatewayClientType.WEBCLIENT;
  }
}
