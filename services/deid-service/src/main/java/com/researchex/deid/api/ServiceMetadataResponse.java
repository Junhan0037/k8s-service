package com.researchex.deid.api;

import java.time.OffsetDateTime;

/** De-identification 서비스 메타데이터 응답 정의. */
public record ServiceMetadataResponse(
    String serviceName, String description, OffsetDateTime timestamp) {}
