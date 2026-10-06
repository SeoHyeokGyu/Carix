package com.carix.pipeline.dto.explore;

import java.util.List;

/** 관리자 수집 데이터 — 최신 기준 연도 임금통계 칸별 출처와 정의·출처별 최신 회차 중위값(전국), 그리고 칸마다 엔진이 실제로 고르는 전국 기준선. 설계 §5.4. */
public record WageTable(
    String basePeriod,
    List<String> families,
    List<String> brackets,
    List<Cell> cells,
    List<String> missing,
    List<Baseline> engineBaselines) {

  public static final List<String> FAMILIES = List.of("DEVELOPER", "DATA", "AI");
  public static final List<String> BRACKETS = List.of("ENTRY", "JUNIOR", "MID", "SENIOR", "LEAD");

  public record Cell(
      String jobFamily, String experienceBracket, List<String> sources, List<Value> values) {}

  /**
   * core-api {@code WageBaselineResolver} 가 전국 경로(사용자 광역 지역 없음)에서 고르는 기준선. 연도를 가리지 않고 고르므로 최신 기준
   * 연도와 다를 수 있다.
   */
  public record Baseline(
      String jobFamily,
      String experienceBracket,
      String region,
      String basePeriod,
      String wageDefinition,
      String source,
      String statVintage,
      long wageMedian) {}

  /** 정의·출처별 최신 회차({@code stat_vintage}) 중위값. 연간 원 단위. */
  public record Value(String wageDefinition, String source, String statVintage, long wageMedian) {}
}
