package com.researchex.userportal.api;

import java.time.OffsetDateTime;

/**
 * User Portal 서비스가 반환하는 메타데이터 응답 모델.
 */
public record ServiceMetadataResponse(
        String serviceName, String description, OffsetDateTime timestamp) {}
