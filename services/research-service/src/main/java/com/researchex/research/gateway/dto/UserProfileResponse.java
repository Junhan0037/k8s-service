package com.researchex.research.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

/** 사용자 포털에서 제공하는 사용자 요약 정보 DTO. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserProfileResponse(
    @JsonProperty("userId") String userId,
    @JsonProperty("displayName") String displayName,
    @JsonProperty("primaryRole") String primaryRole,
    @JsonProperty("active") boolean active,
    @JsonProperty("lastLoginAt") OffsetDateTime lastLoginAt) {}
