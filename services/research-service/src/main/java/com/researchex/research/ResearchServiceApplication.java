package com.researchex.research;

import com.researchex.research.config.SearchCacheProperties;
import com.researchex.research.config.SearchProperties;
import com.researchex.research.gateway.GatewayClientProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Research 서비스 진입점으로, 검색 API 제공과 함께 캐시 및 설정 바인딩을 초기화한다.
 * - {@link EnableCaching} : Redis + Caffeine 조합 캐시 활성화<br>
 * - {@link EnableConfigurationProperties} : 검색 및 캐시 관련 커스텀 프로퍼티를 빈으로 등록
 */
@EnableCaching
@SpringBootApplication
@EnableConfigurationProperties({SearchCacheProperties.class, SearchProperties.class, GatewayClientProperties.class})
public class ResearchServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ResearchServiceApplication.class, args);
    }
}
