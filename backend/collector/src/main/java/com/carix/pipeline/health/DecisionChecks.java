package com.carix.pipeline.health;

import com.carix.pipeline.dto.DataHealthReport;
import com.carix.pipeline.health.DataHealthSnapshot.UserSnapshot;
import com.carix.pipeline.health.DataHealthSnapshot.WageCell;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** ③ 결정 신호 — 「지금 무엇을 해야 하나?」 */
public final class DecisionChecks {

  public static final List<String> BRACKETS = List.of("ENTRY", "JUNIOR", "MID", "SENIOR", "LEAD");
  private static final Set<String> ESTIMATED_WAGE_SOURCES = Set.of("FALLBACK", "UNKNOWN");
  private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");
  private static final String RECALC = "전 사용자 재계산";

  private DecisionChecks() {}

  public static DataHealthReport.Group group(
      DataHealthSnapshot data, YearMonth referenceMonth, String engineRuleVersion) {
    return new DataHealthReport.Group(
        "DECISION",
        "결정 신호",
        "지금 무엇을 해야 하나?",
        List.of(
            wage(data),
            ruleVersion(data, engineRuleVersion),
            freshness(data, referenceMonth),
            quality(data)));
  }

  private static DataHealthReport.Check wage(DataHealthSnapshot data) {
    if (data.wageCells().isEmpty()) {
      return PpiChecks.unknown("WAGE_BASELINE", "연봉 기준선(임금통계)", "임금통계가 없습니다");
    }
    String latest =
        data.wageCells().stream().map(WageCell::basePeriod).max(String::compareTo).orElseThrow();
    List<WageCell> cells =
        data.wageCells().stream().filter(c -> latest.equals(c.basePeriod())).toList();
    long estimated =
        cells.stream().filter(c -> ESTIMATED_WAGE_SOURCES.contains(c.source())).count();
    Set<String> present = new HashSet<>();
    cells.forEach(c -> present.add(c.jobFamily() + "/" + c.experienceBracket()));
    List<String> missing = new ArrayList<>();
    for (String f : MviChecks.TARGET_FAMILIES) {
      for (String b : BRACKETS) {
        if (!present.contains(f + "/" + b)) {
          missing.add(f + "/" + b);
        }
      }
    }
    HealthVerdict v = estimated == 0 && missing.isEmpty() ? HealthVerdict.OK : HealthVerdict.WARN;
    return new DataHealthReport.Check(
        "WAGE_BASELINE",
        "연봉 기준선(임금통계)",
        v,
        latest + " 기준 · 추정 행 " + estimated + " · 빈 칸 " + missing.size(),
        null,
        v == HealthVerdict.OK ? null : "임금통계 재수집",
        v == HealthVerdict.OK ? null : "/admin/collector",
        Map.of("basePeriod", latest, "estimatedRows", estimated, "missingCells", missing));
  }

  private static DataHealthReport.Check ruleVersion(DataHealthSnapshot data, String engine) {
    if (engine == null) {
      return PpiChecks.unknown("RULE_VERSION", "규칙 버전", "엔진 규칙 버전을 받지 못했습니다");
    }
    if (data.snapshots().isEmpty()) {
      return PpiChecks.unknown("RULE_VERSION", "규칙 버전", "사용자 스냅샷이 없습니다");
    }
    List<String> behind = new ArrayList<>();
    data.snapshots().stream()
        .filter(s -> s.ruleVersion() == null || compareVersions(s.ruleVersion(), engine) < 0)
        .forEach(s -> behind.add(prefix(s.userId())));
    HealthVerdict v = behind.isEmpty() ? HealthVerdict.OK : HealthVerdict.WARN;
    return new DataHealthReport.Check(
        "RULE_VERSION",
        "규칙 버전",
        v,
        "엔진 " + engine + " · 뒤처진 사용자 " + behind.size() + "명",
        behind.size(),
        v == HealthVerdict.OK ? null : RECALC,
        null,
        Map.of("engineRuleVersion", engine, "users", behind));
  }

  private static DataHealthReport.Check freshness(
      DataHealthSnapshot data, YearMonth referenceMonth) {
    if (data.snapshots().isEmpty()) {
      return PpiChecks.unknown("SNAPSHOT_FRESHNESS", "스냅샷 최신성", "사용자 스냅샷이 없습니다");
    }
    String current = YM.format(referenceMonth);
    List<String> stale = new ArrayList<>();
    data.snapshots().stream()
        .filter(s -> s.baseYearMonth() == null || s.baseYearMonth().compareTo(current) < 0)
        .forEach(s -> stale.add(prefix(s.userId())));
    HealthVerdict v = stale.isEmpty() ? HealthVerdict.OK : HealthVerdict.WARN;
    return new DataHealthReport.Check(
        "SNAPSHOT_FRESHNESS",
        "스냅샷 최신성",
        v,
        current + " 이전 스냅샷 사용자 " + stale.size() + "명",
        stale.size(),
        v == HealthVerdict.OK ? null : RECALC,
        null,
        Map.of("referenceMonth", current, "users", stale));
  }

  private static DataHealthReport.Check quality(DataHealthSnapshot data) {
    Map<String, Integer> byReason = new LinkedHashMap<>();
    for (UserSnapshot s : data.snapshots()) {
      String reason =
          "INSUFFICIENT_DATA".equals(s.decisionSignal())
              ? "INSUFFICIENT_DATA"
              : s.rulesApplied().contains(SummaryCalculator.MVI_NOT_REFLECTED)
                  ? "MVI_NOT_REFLECTED"
                  : s.rulesApplied().contains(SummaryCalculator.MVI_UNCERTAIN)
                      ? "MVI_UNCERTAIN"
                      : null;
      if (reason != null) {
        byReason.merge(reason, 1, Integer::sum);
      }
    }
    int partial = byReason.values().stream().mapToInt(Integer::intValue).sum();
    return new DataHealthReport.Check(
        "DECISION_QUALITY",
        "판정 품질",
        HealthVerdict.OK,
        "데이터 부족·부분 판정 " + partial + "명 / " + data.snapshots().size() + "명",
        partial,
        null,
        null,
        Map.of("byReason", byReason));
  }

  /** 점으로 나눈 숫자를 차례로 비교한다. 숫자가 아닌 조각은 0 으로 본다. */
  public static int compareVersions(String a, String b) {
    String[] x = a.split("\\.");
    String[] y = b.split("\\.");
    for (int i = 0; i < Math.max(x.length, y.length); i++) {
      int cmp = Integer.compare(part(x, i), part(y, i));
      if (cmp != 0) {
        return cmp;
      }
    }
    return 0;
  }

  private static int part(String[] parts, int i) {
    if (i >= parts.length) {
      return 0;
    }
    try {
      return Integer.parseInt(parts[i]);
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  static String prefix(String userId) {
    return userId == null ? "" : userId.substring(0, Math.min(8, userId.length()));
  }
}
