package com.researchex.mrviewer.api;

import java.time.OffsetDateTime;

/** MR Viewer 서비스가 노출하는 기본 메타데이터 응답. */
public record ServiceMetadataResponse(
    String serviceName, String description, OffsetDateTime timestamp) {}
