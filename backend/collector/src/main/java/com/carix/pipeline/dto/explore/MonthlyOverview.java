package com.carix.pipeline.dto.explore;

import java.util.List;
import java.util.Map;

/** 관리자 수집 데이터 — 월별 현황. 설계 §5.1. */
public record MonthlyOverview(String from, String to, List<Month> months) {

  public record Month(
      String month,
      int jobkoreaSampled,
      int jobkoreaSampledCapped,
      int jobkoreaCrawled,
      int saraminCrawled,
      Map<String, Integer> sampledByFamily,
      int wantedSampled,
      int wantedSampledCapped,
      int wantedCrawled,
      boolean current) {}
}
