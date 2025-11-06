package com.researchex.research.service.search

import co.elastic.clients.elasticsearch._types.SortOrder
import java.util.Locale

/**
 * 사용자가 지정한 정렬 조건을 표현한다.
 */
data class ResearchSortOption(
    val field: String,
    val direction: SortOrder
) {

    /**
     * 캐시 키 생성을 위한 Compact 문자열.
     */
    fun asCacheFragment(): String = "$field:${direction.jsonValue()}"

    companion object {
        /**
         * 정렬 파라미터 문자열을 파싱한다. (예: "updatedAt:desc")
         */
        fun fromParameter(parameter: String?): ResearchSortOption {
            require(!parameter.isNullOrBlank()) { "정렬 파라미터가 비어있습니다." }

            val tokens = parameter.split(":", limit = 2)
            val fieldName = tokens[0].trim()
            val directionToken = if (tokens.size > 1) {
                tokens[1].trim().lowercase(Locale.ROOT)
            } else {
                "desc"
            }

            val order = when (directionToken) {
                "asc", "ascending" -> SortOrder.Asc
                "desc", "descending" -> SortOrder.Desc
                else -> throw IllegalArgumentException("지원하지 않는 정렬 방향: $directionToken")
            }
            return ResearchSortOption(fieldName, order)
        }
    }
}
