package com.researchex.cdwloader.api

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** CDW 배치 적재 요청 본문을 표현한다. */
data class CdwBatchRequest(
    @field:NotBlank(message = "tenantId는 필수입니다.")
    @field:Size(max = 64, message = "tenantId는 최대 64자까지 허용됩니다.")
    val tenantId: String,

    @field:NotBlank(message = "batchId는 필수입니다.")
    @field:Size(max = 128, message = "batchId는 최대 128자까지 허용됩니다.")
    val batchId: String,

    @field:NotBlank(message = "sourceSystem은 필수입니다.")
    @field:Size(max = 128, message = "sourceSystem은 최대 128자까지 허용됩니다.")
    val sourceSystem: String,

    @field:Min(value = 1, message = "recordCount는 1 이상이어야 합니다.")
    val recordCount: Long,
)
