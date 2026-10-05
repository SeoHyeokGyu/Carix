package com.carix.pipeline.dto.explore;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 관리자 수집 데이터 — CPI 월 × 품목. 값이 없는 칸은 키를 두지 않는다. 설계 §5.3. */
public record CpiTable(List<String> codes, List<Month> months, List<Missing> missing) {

  public static final List<String> CODES =
      List.of("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L");

  public record Month(String month, Map<String, BigDecimal> values) {}

  public record Missing(String month, List<String> codes) {}
}
