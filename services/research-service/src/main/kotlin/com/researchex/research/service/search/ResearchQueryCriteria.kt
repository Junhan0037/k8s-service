package com.researchex.research.service.search

/**
 * 검색 요청을 표현하는 도메인 오브젝트.
 * Controller → Service → Repository 간 명확한 계약을 위해 사용한다.
 */
data class ResearchQueryCriteria(
    val query: String?,
    val tenantId: String = "default",
    val queryId: String?,
    val page: Int,
    val size: Int,
    val filters: Map<String, List<String>> = emptyMap(),
    val sorts: List<ResearchSortOption> = emptyList(),
    val useCache: Boolean = true
) {

    /**
     * 캐시 키 생성을 위해 page/size/sort/filter 정보를 고유 문자열로 직렬화한다.
     */
    fun toCacheKeySuffix(): String {
        val builder = StringBuilder()
        builder.append("tenant=").append(tenantId)
            .append("|q=").append(query ?: "")
            .append("|p=").append(page)
            .append("|s=").append(size)

        if (filters.isNotEmpty()) {
            val serializedFilters = filters.entries
                .sortedBy { it.key }
                .joinToString(";") { entry ->
                    "${entry.key}:${entry.value.joinToString(",")}"
                }
            builder.append("|f=").append(serializedFilters)
        }

        if (sorts.isNotEmpty()) {
            val serializedSorts = sorts
                .map { it.asCacheFragment() }
                .sorted()
                .joinToString(",")
            builder.append("|o=").append(serializedSorts)
        }

        return builder.toString()
    }

    companion object {
        fun builder(): Builder = Builder()
    }

    class Builder {
        private var query: String? = null
        private var tenantId: String = "default"
        private var queryId: String? = null
        private var page: Int = 0
        private var size: Int = 20
        private var filters: Map<String, List<String>> = emptyMap()
        private var sorts: List<ResearchSortOption> = emptyList()
        private var useCache: Boolean = true

        fun query(query: String?) = apply { this.query = query }
        fun tenantId(tenantId: String) = apply { this.tenantId = tenantId }
        fun queryId(queryId: String?) = apply { this.queryId = queryId }
        fun page(page: Int) = apply { this.page = page }
        fun size(size: Int) = apply { this.size = size }
        fun filters(filters: Map<String, List<String>>) = apply { this.filters = filters }
        fun sorts(sorts: List<ResearchSortOption>) = apply { this.sorts = sorts }
        fun useCache(useCache: Boolean) = apply { this.useCache = useCache }

        fun build(): ResearchQueryCriteria {
            return ResearchQueryCriteria(
                query = query,
                tenantId = tenantId,
                queryId = queryId,
                page = page,
                size = size,
                filters = filters,
                sorts = sorts,
                useCache = useCache
            )
        }
    }
}
