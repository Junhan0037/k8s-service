package com.researchex.research.service.dto

/**
 * 페이징 정보 DTO.
 */
data class PaginationMetadata(
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean
) {
    companion object {
        /**
         * 페이지 정보를 계산하는 팩토리 메서드.
         *
         * @param page          0-based 페이지
         * @param size          페이지 크기
         * @param totalElements 전체 건수
         * @return 메타데이터 객체
         */
        fun from(page: Int, size: Int, totalElements: Long): PaginationMetadata {
            val totalPages = if (size == 0) 0 else kotlin.math.ceil(totalElements.toDouble() / size).toInt()
            val hasNext = page + 1 < totalPages
            return PaginationMetadata(page, size, totalElements, totalPages, hasNext)
        }
    }
}
