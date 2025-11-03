package com.researchex.cdwloader.api;

import java.time.Instant;

/** 배치 적재 요청 수락 시 반환하는 응답 모델. */
public record CdwBatchResponse(String batchId, String tenantId, Instant acceptedAt) {}
