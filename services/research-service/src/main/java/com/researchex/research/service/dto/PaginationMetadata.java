package com.researchex.research.service.dto;

/**
 * 페이징 정보 DTO.
 */
public record PaginationMetadata(
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    /**
     * 페이지 정보를 계산하는 팩토리 메서드.
     *
     * @param page          0-based 페이지
     * @param size          페이지 크기
     * @param totalElements 전체 건수
     * @return 메타데이터 객체
     */
    public static PaginationMetadata from(int page, int size, long totalElements) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        boolean hasNext = page + 1 < totalPages;
        return new PaginationMetadata(page, size, totalElements, totalPages, hasNext);
    }
}
