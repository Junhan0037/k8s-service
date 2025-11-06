package com.researchex.research.domain

import java.time.Instant

/** Elasticsearch 인덱스에 저장될 문서 모델이다. */
data class ResearchIndexDocument(
    val documentId: String,
    val tenantId: String,
    val jobId: String,
    val payloadLocation: String,
    val indexedAt: Instant
)
