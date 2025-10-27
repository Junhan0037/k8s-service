-- ResearchEx 로컬 개발 환경용 기본 데이터베이스 스키마
-- 추후 서비스별 마이그레이션 도구(Flyway/Liquibase) 적용 전까지 최소한의 테이블을 정의한다.

CREATE SCHEMA IF NOT EXISTS researchex_core;

-- TODO: 서비스별 DDL은 각 모듈에서 정의하고, 이 파일에서는 공용 오브젝트만 유지한다.
