package com.researchex.research.domain

import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import java.time.OffsetDateTime

/**
 * Elasticsearch에 저장된 연구(Clinical Study) 문서를 표현한다.
 * 검색 성능을 위해 필요한 필드만 저장하며, 상세 정보는 별도 서비스에서 조회한다.
 */
@Document(indexName = ResearchDocument.INDEX_NAME)
data class ResearchDocument(
    @Id
    var id: String? = null,

    /** 연구 제목 - 가중치를 높여 검색 결과 상위에 노출한다. */
    @Field(type = FieldType.Text)
    var title: String? = null,

    /** 연구 요약문. 전문(full text) 대신 주요 포인트만 저장해 인덱스 용량을 최적화한다. */
    @Field(type = FieldType.Text)
    var summary: String? = null,

    /** 질병 코드(ICD 등)를 태그 형태로 저장해 filtering 조건으로 활용한다. */
    @Field(type = FieldType.Keyword)
    var diseaseCodes: List<String>? = null,

    /** 사용자 정의 태그(해당 연구가 다루는 키워드). */
    @Field(type = FieldType.Keyword)
    var tags: List<String>? = null,

    /** 책임 연구자 ID. 상세 정보는 User Portal을 통해 별도 조회한다. */
    @Field(name = "principal_investigator_id", type = FieldType.Keyword)
    var principalInvestigatorId: String? = null,

    /** 책임 연구자 이름. 프리젠테이션 용도로 저장하며, 정합성 강화를 위해 ID와 함께 사용한다. */
    @Field(name = "principal_investigator_name", type = FieldType.Keyword)
    var principalInvestigatorName: String? = null,

    /** 연구 수행 기관. */
    @Field(type = FieldType.Keyword)
    var institution: String? = null,

    /** 임상 단계(Phase). */
    @Field(type = FieldType.Keyword)
    var phase: String? = null,

    /** 등록 상태(예: Recruiting, Completed). */
    @Field(type = FieldType.Keyword)
    var status: String? = null,

    /** 등록 대상자 수(예상치). 정렬 조건에 사용된다. */
    @Field(type = FieldType.Double)
    var enrollment: Double? = null,

    /** 문서 생성일. 최신순 정렬 및 증분 동기화에 사용한다. */
    @Field(name = "created_at", type = FieldType.Date)
    var createdAt: OffsetDateTime? = null,

    /** 문서 갱신일. 캐시 무효화 및 최신순 정렬에 활용한다. */
    @Field(name = "updated_at", type = FieldType.Date)
    var updatedAt: OffsetDateTime? = null
) {
    companion object {
        const val INDEX_NAME: String = "research-studies"
    }
}
