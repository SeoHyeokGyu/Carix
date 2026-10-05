package com.carix.pipeline.dto.explore;

import java.util.List;

/** 관리자 수집 데이터 — 최신 기준 연도 임금통계 칸별 출처. 금액은 싣지 않는다. 설계 §5.4. */
public record WageTable(
    String basePeriod,
    List<String> families,
    List<String> brackets,
    List<Cell> cells,
    List<String> missing) {

  public static final List<String> FAMILIES = List.of("DEVELOPER", "DATA", "AI");
  public static final List<String> BRACKETS = List.of("ENTRY", "JUNIOR", "MID", "SENIOR", "LEAD");

  public record Cell(String jobFamily, String experienceBracket, List<String> sources) {}
}
