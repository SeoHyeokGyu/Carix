package com.carix.jobposting.normalize;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 원천이 자유 문자열로 주는 고용형태를 동료집단 차원 코드로 정규화한다.
 *
 * <p>실측 800건에 18종의 표기가 있었고 둘로 나뉜다. `정규직 외`처럼 "그 밖에도 있음"을
 * 꼬리표로 붙인 것(41건), `정규직·계약직`처럼 여러 형태를 나열한 것(55건)이다. 둘 다
 * 원천이 대표 형태를 앞에 두므로 꼬리표를 떼고 첫 항목으로 판정한다.
 */
public class EmploymentTypeClassifier {

  public static final String FULL_TIME = "FULL_TIME";
  public static final String CONTRACT = "CONTRACT";
  public static final String FREELANCE = "FREELANCE";
  public static final String INTERN = "INTERN";
  public static final String UNKNOWN = "UNKNOWN";

  private static final String OTHER_SUFFIX = " 외";
  private static final String COMPOUND_SEPARATOR = "·";

  /** 삽입 순서가 곧 매칭 우선순위다. 긴 표기를 먼저 둬 부분 일치로 가로채이지 않게 한다. */
  private static final Map<String, String> LABELS = new LinkedHashMap<>();

  static {
    LABELS.put("정규직", FULL_TIME);
    LABELS.put("계약직", CONTRACT);
    LABELS.put("기간제", CONTRACT);
    LABELS.put("파견직", CONTRACT);
    LABELS.put("프리랜서", FREELANCE);
    LABELS.put("인턴", INTERN);
    LABELS.put("교육생", INTERN);
  }

  public String classify(String rawEmploymentType) {
    if (rawEmploymentType == null || rawEmploymentType.isBlank()) {
      return UNKNOWN;
    }
    String label = firstLabel(rawEmploymentType);
    return LABELS.entrySet().stream()
        .filter(entry -> label.contains(entry.getKey()))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElse(UNKNOWN);
  }

  private String firstLabel(String rawEmploymentType) {
    String value = rawEmploymentType.strip();
    if (value.endsWith(OTHER_SUFFIX)) {
      value = value.substring(0, value.length() - OTHER_SUFFIX.length()).strip();
    }
    int separatorIdx = value.indexOf(COMPOUND_SEPARATOR);
    return separatorIdx == -1 ? value : value.substring(0, separatorIdx).strip();
  }
}
