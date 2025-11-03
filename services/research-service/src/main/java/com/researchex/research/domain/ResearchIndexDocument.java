package com.researchex.research.domain;

import java.time.Instant;

/** Elasticsearch 인덱스에 저장될 문서 모델. */
public record ResearchIndexDocument(
    String documentId, String tenantId, String jobId, String payloadLocation, Instant indexedAt) {}
