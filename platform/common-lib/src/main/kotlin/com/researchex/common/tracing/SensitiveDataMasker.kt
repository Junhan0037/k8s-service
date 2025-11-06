package com.researchex.common.tracing

import java.util.regex.Pattern

/** 로그에 출력되는 개인정보를 간단히 마스킹한다. */
object SensitiveDataMasker {

    private val RESIDENT_ID: Pattern = Pattern.compile("\\b(\\d{6})-?(\\d{7})\\b")
    private val PHONE_NUMBER: Pattern = Pattern.compile("\\b(01\\d|02|0\\d{2})-?(\\d{3,4})-?(\\d{4})\\b")
    private val EMAIL: Pattern = Pattern.compile("([\\w.%-])([\\w.%+-]*)(@[^\\s]+)")

    /** 사전 정의된 패턴을 이용해 민감값을 마스킹한다. */
    fun mask(raw: String?): String {
        if (raw.isNullOrBlank()) {
            return raw ?: ""
        }
        var masked = RESIDENT_ID.matcher(raw).replaceAll("$1-*******")
        masked = PHONE_NUMBER.matcher(masked).replaceAll("***-***-$3")
        return EMAIL.matcher(masked).replaceAll("$1***$3")
    }
}
