package com.researchex.cdwloader.api;

import java.time.OffsetDateTime;

/**
 * CDW Loader 서비스 메타데이터 응답 구조.
 */
public record ServiceMetadataResponse(
        String serviceName, String description, OffsetDateTime timestamp) {}
