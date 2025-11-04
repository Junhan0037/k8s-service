package com.researchex.research.service.search;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.researchex.research.config.SearchProperties;
import com.researchex.research.domain.ResearchDocument;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregation;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Elasticsearch 를 이용해 연구 문서를 검색하는 구현체.
 * 텍스트 검색, 필터, 정렬, facet aggregation을 단일 쿼리로 처리해 N+1 호출을 차단한다.
 */
@Repository
public class ElasticsearchResearchSearchRepository implements ResearchSearchRepository {

    private static final Set<String> DEFAULT_SEARCH_FIELDS = Set.of(
            "title^3",
            "summary^2",
            "tags^1.5",
            "diseaseCodes^1.2",
            "institution",
            "principalInvestigatorName"
    );

    private static final List<String> DEFAULT_AGGREGATION_FIELDS = List.of("phase", "status", "institution");

    private final ElasticsearchOperations operations;
    private final SearchProperties properties;

    public ElasticsearchResearchSearchRepository(ElasticsearchOperations operations, SearchProperties properties) {
        this.operations = operations;
        this.properties = properties;
    }

    @Override
    public SearchResult search(ResearchQueryCriteria criteria) {
        NativeQuery query = buildQuery(criteria);
        SearchHits<ResearchDocument> searchHits = operations.search(query, ResearchDocument.class);

        List<ResearchDocument> documents = searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());

        Map<String, List<FacetBucket>> facets = extractFacets(searchHits);

        return new SearchResult(documents, searchHits.getTotalHits(), facets);
    }

    private NativeQuery buildQuery(ResearchQueryCriteria criteria) {
        NativeQueryBuilder builder = NativeQuery.builder()
                .withPageable(toPageable(criteria))
                .withTrackTotalHits(true)
                .withTimeout(properties.getQueryTimeout());

        builder.withQuery(buildBoolQuery(criteria));

        List<SortOptions> sortOptions = resolveSorts(criteria);

        if (!sortOptions.isEmpty()) {
            builder.withSort(sortOptions);
        }

        DEFAULT_AGGREGATION_FIELDS.forEach(field -> builder.withAggregation(field, buildTermsAggregation(field)));

        return builder.build();
    }

    private Pageable toPageable(ResearchQueryCriteria criteria) {
        return PageRequest.of(criteria.getPage(), criteria.getSize());
    }

    private Query buildBoolQuery(ResearchQueryCriteria criteria) {
        return Query.of(q -> q.bool(bool -> {
            if (StringUtils.hasText(criteria.getQuery())) {
                bool.must(m -> m.simpleQueryString(s -> s
                        .query(criteria.getQuery())
                        .fields(new ArrayList<>(DEFAULT_SEARCH_FIELDS))
                        .defaultOperator(co.elastic.clients.elasticsearch._types.query_dsl.Operator.And)
                ));
            } else {
                bool.must(m -> m.matchAll(ma -> ma));
            }

            criteria.getFilters().forEach((field, values) -> {
                if (values == null || values.isEmpty()) {
                    return;
                }
                bool.filter(f -> f.terms(t -> t
                        .field(field)
                        .terms(term -> term.value(values.stream()
                                .filter(StringUtils::hasText)
                                .map(value -> FieldValue.of(value.toLowerCase(Locale.ROOT)))
                                .toList()))
                ));
            });

            return bool;
        }));
    }

    private List<SortOptions> resolveSorts(ResearchQueryCriteria criteria) {
        if (criteria.getSorts().isEmpty()) {
            return List.of(
                    SortOptions.of(s -> s.field(f -> f.field("updatedAt").order(SortOrder.Desc))),
                    SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc)))
            );
        }

        List<SortOptions> sortOptions = new ArrayList<>(criteria.getSorts().size() + 1);

        for (ResearchSortOption sort : criteria.getSorts()) {
            String field = normalizeSortField(sort.field());
            sortOptions.add(SortOptions.of(s -> s.field(f -> f.field(field).order(sort.direction()))));
        }

        // score 정렬은 항상 맨 뒤에 붙여 relevance 를 보정한다.
        sortOptions.add(SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc))));

        return sortOptions;
    }

    private String normalizeSortField(String requestedField) {
        if (requestedField == null) {
            return "updatedAt";
        }

        String normalized = requestedField.trim();
        List<String> allowed = properties.getSortableFields();

        if (allowed.contains(normalized)) {
            return normalized;
        }

        // whitelist 에 포함되지 않은 경우 안전한 기본값으로 대체한다.
        return allowed.isEmpty() ? "updatedAt" : allowed.get(0);
    }

    private Aggregation buildTermsAggregation(String field) {
        return Aggregation.of(a -> a.terms(t -> t
                .field(field)
                .size(15)
        ));
    }

    private Map<String, List<FacetBucket>> extractFacets(SearchHits<ResearchDocument> hits) {
        if (!hits.hasAggregations()) {
            return Collections.emptyMap();
        }
        if (!(hits.getAggregations() instanceof ElasticsearchAggregations aggregations)) {
            return Collections.emptyMap();
        }

        Map<String, ElasticsearchAggregation> aggregationMap = aggregations.aggregationsAsMap();
        Map<String, List<FacetBucket>> facetMap = new LinkedHashMap<>();

        for (String field : DEFAULT_AGGREGATION_FIELDS) {
            ElasticsearchAggregation aggregation = aggregationMap.get(field);
            if (aggregation == null) {
                continue;
            }
            Aggregate aggregate = aggregation.aggregation().getAggregate();
            if (!aggregate.isSterms()) {
                continue;
            }
            StringTermsAggregate termsAggregate = aggregate.sterms();
            List<FacetBucket> buckets = extractStringFacetBuckets(termsAggregate);
            facetMap.put(field, buckets);
        }

        return facetMap;
    }

    private List<FacetBucket> extractStringFacetBuckets(StringTermsAggregate aggregate) {
        if (aggregate == null || aggregate.buckets() == null) {
            return Collections.emptyList();
        }

        List<FacetBucket> buckets = new ArrayList<>();
        var rawBuckets = aggregate.buckets();

        if (rawBuckets.isArray()) {
            for (StringTermsBucket bucket : rawBuckets.array()) {
                String key = bucket.key() != null && bucket.key().isString() ? bucket.key().stringValue() : bucket.key().toString();
                buckets.add(new FacetBucket(key, bucket.docCount()));
            }
        }

        return buckets;
    }
}
