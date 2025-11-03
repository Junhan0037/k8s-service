package com.researchex.common.tracing;

import java.util.regex.Pattern;

/** 로그에 출력되는 개인정보를 간단히 마스킹한다. */
public final class SensitiveDataMasker {

  private static final Pattern RESIDENT_ID = Pattern.compile("\\b(\\d{6})-?(\\d{7})\\b");
  private static final Pattern PHONE_NUMBER =
      Pattern.compile("\\b(01\\d|02|0\\d{2})-?(\\d{3,4})-?(\\d{4})\\b");
  private static final Pattern EMAIL = Pattern.compile("([\\w.%-])([\\w.%+-]*)(@[^\\s]+)");

  private SensitiveDataMasker() {}

  /** 사전 정의된 패턴을 이용해 민감값을 마스킹한다. */
  public static String mask(String raw) {
    if (raw == null || raw.isBlank()) {
      return raw;
    }
    String masked = RESIDENT_ID.matcher(raw).replaceAll("$1-*******");
    masked = PHONE_NUMBER.matcher(masked).replaceAll("***-***-$3");
    return EMAIL.matcher(masked).replaceAll("$1***$3");
  }
}
