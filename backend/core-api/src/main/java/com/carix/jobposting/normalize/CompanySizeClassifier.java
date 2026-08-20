package com.carix.jobposting.normalize;

import java.util.List;

/**
 * 원천이 주는 기업 정보를 동료집단 차원인 회사 규모로 정규화한다.
 *
 * <p>사원수를 우선 본다. 실측 400건 중 386건(96.5%)에 값이 있고, 기업 구분 표기보다
 * 원천 간 해석이 흔들리지 않는다. 300명 경계는 중소기업기본법의 상시근로자 기준을 따랐다.
 *
 * <p>사람인은 목록페이지에 기업 규모를 싣지 않아 판정 근거가 없다 — 호출부에서
 * {@link #UNKNOWN} 을 넣는다. `market_aggregate` 에서 UNKNOWN 은 전체 롤업(NULL)과
 * 구분되는 별도 버킷이므로 NULL 로 두지 않는다.
 */
public class CompanySizeClassifier {

  public static final String LARGE = "LARGE";
  public static final String MEDIUM = "MEDIUM";
  public static final String STARTUP = "STARTUP";
  public static final String UNKNOWN = "UNKNOWN";

  private static final int LARGE_THRESHOLD = 300;
  private static final int STARTUP_THRESHOLD = 50;

  /** 사원수가 없을 때만 쓰는 기업 구분 표기. 규모를 특정할 수 없는 표기는 넣지 않는다. */
  private static final List<String> LARGE_TYPES = List.of("대기업", "중견기업", "공공기관", "공기업");
  private static final List<String> MEDIUM_TYPES = List.of("중소기업");
  private static final List<String> STARTUP_TYPES = List.of("벤처기업");

  public String classify(Integer employeeCount, String companyTypeName) {
    if (employeeCount != null && employeeCount > 0) {
      if (employeeCount >= LARGE_THRESHOLD) {
        return LARGE;
      }
      return employeeCount >= STARTUP_THRESHOLD ? MEDIUM : STARTUP;
    }
    return fromCompanyType(companyTypeName);
  }

  private String fromCompanyType(String companyTypeName) {
    if (companyTypeName == null || companyTypeName.isBlank()) {
      return UNKNOWN;
    }
    String type = companyTypeName.strip();
    if (containsAny(type, LARGE_TYPES)) {
      return LARGE;
    }
    if (containsAny(type, MEDIUM_TYPES)) {
      return MEDIUM;
    }
    return containsAny(type, STARTUP_TYPES) ? STARTUP : UNKNOWN;
  }

  private boolean containsAny(String type, List<String> keywords) {
    return keywords.stream().anyMatch(type::contains);
  }
}
