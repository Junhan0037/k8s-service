package com.researchex.mrviewer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 의무기록 뷰어 서비스의 진입점으로 API 서버를 부트스트랩한다.
 */
@SpringBootApplication
public class MrViewerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MrViewerServiceApplication.class, args);
    }
}
