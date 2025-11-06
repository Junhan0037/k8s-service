package com.researchex.research.gateway.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.OffsetDateTime

/** 사용자 포털에서 제공하는 사용자 요약 정보 DTO다. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class UserProfileResponse(
    @JsonProperty("userId")
    val userId: String,

    @JsonProperty("displayName")
    val displayName: String,

    @JsonProperty("primaryRole")
    val primaryRole: String,

    @JsonProperty("active")
    val active: Boolean,

    @JsonProperty("lastLoginAt")
    val lastLoginAt: OffsetDateTime
)
