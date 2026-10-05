package com.carix.pipeline.health;

import com.carix.pipeline.dto.DataHealthReport;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** ① 구매력 지수 — 「내 실질 구매력은?」 */
public final class PpiChecks {

  public static final int CPI_CODE_COUNT = 12;
  private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");

  private PpiChecks() {}

  public static DataHealthReport.Group group(
      DataHealthSnapshot data, YearMonth referenceMonth, HealthThresholds t) {
    return new DataHealthReport.Group(
        "PPI", "구매력 지수", "내 실질 구매력은?", List.of(latest(data, referenceMonth, t), gap(data)));
  }

  private static DataHealthReport.Check latest(
      DataHealthSnapshot data, YearMonth referenceMonth, HealthThresholds t) {
    if (data.cpiCodeCountByMonth().isEmpty()) {
      return unknown("CPI_LATEST", "CPI 최신월", "CPI 적재 기록이 없습니다");
    }
    String latest = new TreeSet<>(data.cpiCodeCountByMonth().keySet()).last();
    long lag = ChronoUnit.MONTHS.between(YearMonth.parse(latest, YM), referenceMonth);
    int delayed = countPpi(data, "UPDATE_DELAYED");
    HealthVerdict v =
        lag > t.cpiMaxLagMonths()
            ? HealthVerdict.ERROR
            : lag == t.cpiMaxLagMonths() ? HealthVerdict.WARN : HealthVerdict.OK;
    String action =
        v == HealthVerdict.OK ? null : v == HealthVerdict.WARN ? "다음 달 이전에 CPI 적재" : "CPI 적재";
    return new DataHealthReport.Check(
        "CPI_LATEST",
        "CPI 최신월",
        v,
        "최신 CPI " + latest + " (" + lag + "개월 지연)",
        delayed,
        action,
        v == HealthVerdict.OK ? null : "/admin/collector",
        Map.of("latestCpiMonth", latest, "lagMonths", lag));
  }

  private static DataHealthReport.Check gap(DataHealthSnapshot data) {
    if (data.cpiCodeCountByMonth().isEmpty()) {
      return unknown("CPI_GAP", "CPI 중간 결측", "CPI 적재 기록이 없습니다");
    }
    TreeSet<String> months = new TreeSet<>(data.cpiCodeCountByMonth().keySet());
    List<String> incomplete = new ArrayList<>();
    for (YearMonth m = YearMonth.parse(months.first(), YM);
        !m.isAfter(YearMonth.parse(months.last(), YM));
        m = m.plusMonths(1)) {
      String key = YM.format(m);
      if (data.cpiCodeCountByMonth().getOrDefault(key, 0) < CPI_CODE_COUNT) {
        incomplete.add(key);
      }
    }
    HealthVerdict v = incomplete.isEmpty() ? HealthVerdict.OK : HealthVerdict.ERROR;
    return new DataHealthReport.Check(
        "CPI_GAP",
        "CPI 중간 결측",
        v,
        incomplete.isEmpty() ? "결측 달 없음" : "결측 달 " + incomplete.size() + "개",
        countPpi(data, "NOT_AVAILABLE"),
        v == HealthVerdict.OK ? null : "결측 월 CPI 재적재",
        v == HealthVerdict.OK ? null : "/admin/collector",
        Map.of("incompleteMonths", incomplete));
  }

  private static int countPpi(DataHealthSnapshot data, String status) {
    return (int) data.snapshots().stream().filter(s -> status.equals(s.ppiStatus())).count();
  }

  static DataHealthReport.Check unknown(String id, String title, String message) {
    return new DataHealthReport.Check(
        id, title, HealthVerdict.UNKNOWN, message, null, null, null, Map.of());
  }
}
