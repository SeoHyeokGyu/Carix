package com.carix.pipeline.health;

import com.carix.pipeline.dto.DataHealthReport;
import java.time.YearMonth;
import java.util.List;

/** 원티드(도입 중) — 「원티드 표본이 쌓이고 있나?」. 요약 판정에는 넣지 않고 같은 표본 점검을 원티드 시계열에 적용한다. */
public final class WantedChecks {

  public static final String GROUP_ID = "WANTED";
  private static final String PREFIX = "WANTED_";

  private WantedChecks() {}

  public static DataHealthReport.Group group(
      DataHealthSnapshot data, YearMonth previousMonth, HealthThresholds t) {
    var series = data.wanted();
    return new DataHealthReport.Group(
        GROUP_ID,
        "원티드(도입 중)",
        "원티드 표본이 쌓이고 있나?",
        List.of(
            MviChecks.lastMonth(series, previousMonth, t, PREFIX, null),
            MviChecks.gaps(series, previousMonth, PREFIX),
            MviChecks.aggregateStale(series, PREFIX, null)));
  }
}
