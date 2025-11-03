package com.researchex.platform.cache;

/**
 * 서비스 전반에서 동일한 캐시 키 네임스페이스를 활용하기 위해 상수 클래스로 정의한다. 캐시 이름을
 * 하드코딩하지 않음으로써 캐시 정책 변경 시 컴파일 타임에 검증할 수 있다.
 */
public final class CacheNames {

  /** 정적인 참고 데이터를 저장하는 캐시 이름(1시간 TTL). */
  public static final String STATIC_REFERENCE = "researchex::static-reference";

  /** 동적 질의 결과를 저장하는 캐시 이름(10분 TTL). */
  public static final String DYNAMIC_QUERY = "researchex::dynamic-query";

  private CacheNames() {
    throw new IllegalStateException("Utility class");
  }
}
