package com.researchex.research.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

/** 의무기록 뷰어 서비스가 반환하는 환자 요약 정보 DTO. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MedicalRecordSummaryResponse(
    @JsonProperty("patientId") String patientId,
    @JsonProperty("recordId") String recordId,
    @JsonProperty("summary") String summary,
    @JsonProperty("lastUpdatedAt") OffsetDateTime lastUpdatedAt) {}
