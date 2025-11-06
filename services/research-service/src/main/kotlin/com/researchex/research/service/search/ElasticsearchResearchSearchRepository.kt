package com.researchex.research.service.search

import co.elastic.clients.elasticsearch._types.FieldValue
import co.elastic.clients.elasticsearch._types.SortOptions
import co.elastic.clients.elasticsearch._types.SortOrder
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket
import co.elastic.clients.elasticsearch._types.query_dsl.Operator
import co.elastic.clients.elasticsearch._types.query_dsl.Query
import com.researchex.research.config.SearchProperties
import com.researchex.research.domain.ResearchDocument
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregation
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations
import org.springframework.data.elasticsearch.client.elc.NativeQuery
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.SearchHit
import org.springframework.data.elasticsearch.core.SearchHits
import org.springframework.stereotype.Repository
import org.springframework.util.StringUtils
import java.util.Locale

/**
 * Elasticsearch 를 이용해 연구 문서를 검색하는 구현체.
 * 텍스트 검색, 필터, 정렬, facet aggregation을 단일 쿼리로 처리해 N+1 호출을 차단한다.
 */
@Repository
class ElasticsearchResearchSearchRepository(
    private val operations: ElasticsearchOperations,
    private val properties: SearchProperties
) : ResearchSearchRepository {

    override fun search(criteria: ResearchQueryCriteria): SearchResult {
        val query = buildQuery(criteria)
        val searchHits: SearchHits<ResearchDocument> = operations.search(query, ResearchDocument::class.java)

        val documents = searchHits.searchHits.map(SearchHit<ResearchDocument>::getContent)
        val facets = extractFacets(searchHits)

        return SearchResult(documents, searchHits.totalHits, facets)
    }

    private fun buildQuery(criteria: ResearchQueryCriteria): NativeQuery {
        val builder = NativeQuery.builder()
            .withPageable(toPageable(criteria))
            .withTrackTotalHits(true)
            .withTimeout(properties.queryTimeout)

        builder.withQuery(buildBoolQuery(criteria))

        val sortOptions = resolveSorts(criteria)
        if (sortOptions.isNotEmpty()) {
            builder.withSort(sortOptions)
        }

        DEFAULT_AGGREGATION_FIELDS.forEach { field ->
            builder.withAggregation(field, buildTermsAggregation(field))
        }

        return builder.build()
    }

    private fun toPageable(criteria: ResearchQueryCriteria): Pageable {
        return PageRequest.of(criteria.page, criteria.size)
    }

    private fun buildBoolQuery(criteria: ResearchQueryCriteria): Query {
        return Query.of { q ->
            q.bool { bool ->
                if (!criteria.query.isNullOrBlank()) {
                    bool.must { mustBuilder ->
                        mustBuilder.simpleQueryString { sqs ->
                            sqs.query(criteria.query)
                                .fields(DEFAULT_SEARCH_FIELDS.toMutableList())
                                .defaultOperator(Operator.And)
                        }
                    }
                } else {
                    bool.must { mustBuilder -> mustBuilder.matchAll { it } }
                }

                criteria.filters.forEach { (field, values) ->
                    if (values.isNullOrEmpty()) {
                        return@forEach
                    }
                    val termValues = values
                        .filter { StringUtils.hasText(it) }
                        .map { value -> FieldValue.of(value.lowercase(Locale.ROOT)) }

                    if (termValues.isEmpty()) {
                        return@forEach
                    }

                    bool.filter { filterBuilder ->
                        filterBuilder.terms { terms ->
                            terms.field(field).terms { term -> term.value(termValues) }
                        }
                    }
                }

                bool
            }
        }
    }

    private fun resolveSorts(criteria: ResearchQueryCriteria): List<SortOptions> {
        if (criteria.sorts.isEmpty()) {
            return listOf(
                SortOptions.of { s -> s.field { f -> f.field("updatedAt").order(SortOrder.Desc) } },
                SortOptions.of { s -> s.score { sc -> sc.order(SortOrder.Desc) } }
            )
        }

        val sortOptions = mutableListOf<SortOptions>()
        criteria.sorts.forEach { sort ->
            val field = normalizeSortField(sort.field)
            sortOptions += SortOptions.of { s ->
                s.field { f -> f.field(field).order(sort.direction) }
            }
        }

        // score 정렬은 항상 맨 뒤에 붙여 relevance 를 보정한다.
        sortOptions += SortOptions.of { s -> s.score { sc -> sc.order(SortOrder.Desc) } }
        return sortOptions
    }

    private fun normalizeSortField(requestedField: String?): String {
        if (requestedField == null) {
            return "updatedAt"
        }
        val normalized = requestedField.trim()
        val allowed = properties.sortableFields
        return if (allowed.contains(normalized)) normalized else allowed.firstOrNull() ?: "updatedAt"
    }

    private fun buildTermsAggregation(field: String): Aggregation {
        return Aggregation.of { aggregation ->
            aggregation.terms { terms ->
                terms.field(field).size(15)
            }
        }
    }

    private fun extractFacets(hits: SearchHits<ResearchDocument>): Map<String, List<FacetBucket>> {
        if (!hits.hasAggregations()) {
            return emptyMap()
        }
        val aggregations = hits.aggregations
        if (aggregations !is ElasticsearchAggregations) {
            return emptyMap()
        }

        val aggregationMap: Map<String, ElasticsearchAggregation> = aggregations.aggregationsAsMap()
        val facetMap = LinkedHashMap<String, List<FacetBucket>>()

        for (field in DEFAULT_AGGREGATION_FIELDS) {
            val aggregation = aggregationMap[field] ?: continue
            val aggregate: Aggregate = aggregation.aggregation().aggregate()
            if (!aggregate.isSterms) {
                continue
            }
            val termsAggregate = aggregate.sterms()
            val buckets = extractStringFacetBuckets(termsAggregate)
            facetMap[field] = buckets
        }

        return facetMap
    }

    private fun extractStringFacetBuckets(aggregate: StringTermsAggregate?): List<FacetBucket> {
        if (aggregate == null) {
            return emptyList()
        }
        val rawBuckets = aggregate.buckets()
        if (!rawBuckets.isArray) {
            return emptyList()
        }
        val result = mutableListOf<FacetBucket>()
        rawBuckets.array().forEach { bucket: StringTermsBucket ->
            val key = when {
                bucket.key() == null -> ""
                bucket.key().isString -> bucket.key().stringValue()
                else -> bucket.key().toString()
            }
            result += FacetBucket(key, bucket.docCount())
        }
        return result
    }

    companion object {
        private val DEFAULT_SEARCH_FIELDS: Set<String> = setOf(
            "title^3",
            "summary^2",
            "tags^1.5",
            "diseaseCodes^1.2",
            "institution",
            "principalInvestigatorName"
        )

        private val DEFAULT_AGGREGATION_FIELDS: List<String> = listOf("phase", "status", "institution")
    }
}
