package com.carix.pipeline.health;

import com.carix.pipeline.dto.DataHealthReport.Check;
import com.carix.pipeline.health.DataHealthSnapshot.SampleSeries;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** 데이터 상태 점검 다섯 개. 순서는 {@link #all} 이 정한다. */
public final class HealthChecks {

  private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");
  private static final String COLLECTOR = "/admin/collector";
  private static final String RECALC = "전 사용자 재계산";

  private HealthChecks() {}

  public static List<Check> all(
      DataHealthSnapshot data,
      YearMonth referenceMonth,
      String engineRuleVersion,
      HealthThresholds t,
      Instant now) {
    return List.of(
        aggregateStale(data.jobKorea(), data.wanted()),
        ruleVersion(data, engineRuleVersion),
        snapshotFreshness(data, referenceMonth),
        cpiLatest(data, referenceMonth, t),
        crawlFreshness(data, now, t));
  }

  public static Check unknown(String id, String title, String message) {
    return new Check(id, title, HealthVerdict.UNKNOWN, message, null, null, null, Map.of());
  }

  /** 잡코리아 불일치는 ERROR, 원티드만 불일치면 WARN(지수 편입 전). 표본이 있는 원천만 비교한다. */
  private static Check aggregateStale(SampleSeries jobKorea, SampleSeries wanted) {
    String id = "AGGREGATE_STALE";
    String title = "재집계 누락";
    if (jobKorea.sampledByMonth().isEmpty() && wanted.sampledByMonth().isEmpty()) {
      return unknown(id, title, "표본이 하나도 없습니다");
    }
    List<String> jkStale = staleMonths(jobKorea);
    List<String> wantedStale = staleMonths(wanted);
    if (jkStale == null || wantedStale == null) {
      return unknown(id, title, "상한 적용 표본을 계산하지 못했습니다");
    }
    HealthVerdict v =
        !jkStale.isEmpty()
            ? HealthVerdict.ERROR
            : !wantedStale.isEmpty() ? HealthVerdict.WARN : HealthVerdict.OK;
    int total = jkStale.size() + wantedStale.size();
    Map<String, Object> evidence = new LinkedHashMap<>();
    evidence.put("JOBKOREA", jkStale);
    evidence.put("WANTED", wantedStale);
    return new Check(
        id,
        title,
        v,
        total == 0 ? "표본과 집계 일치" : "불일치 " + total + "개월",
        null,
        v == HealthVerdict.OK ? null : "재집계",
        v == HealthVerdict.OK ? null : COLLECTOR,
        evidence);
  }

  /** 표본과 집계가 어긋난 달. 표본 없는 원천은 빈 목록, 상한 적용 표본을 못 구한 달이 있으면 {@code null}. */
  private static List<String> staleMonths(SampleSeries data) {
    if (data.sampledByMonth().isEmpty()) {
      return List.of();
    }
    TreeSet<String> months = new TreeSet<>(data.sampledByMonth().keySet());
    months.addAll(data.cappedByMonth().keySet());
    months.addAll(data.aggregatedByMonth().keySet());
    List<String> stale = new ArrayList<>();
    for (String month : months) {
      Integer capped = data.cappedByMonth().get(month);
      if (capped == null) {
        return null;
      }
      if (!capped.equals(data.aggregatedByMonth().get(month))) {
        stale.add(month);
      }
    }
    return stale;
  }

  private static Check ruleVersion(DataHealthSnapshot data, String engine) {
    if (engine == null) {
      return unknown("RULE_VERSION", "규칙 버전", "엔진 규칙 버전을 받지 못했습니다");
    }
    if (data.snapshots().isEmpty()) {
      return unknown("RULE_VERSION", "규칙 버전", "사용자 스냅샷이 없습니다");
    }
    List<String> behind = new ArrayList<>();
    data.snapshots().stream()
        .filter(s -> s.ruleVersion() == null || compareVersions(s.ruleVersion(), engine) < 0)
        .forEach(s -> behind.add(prefix(s.userId())));
    HealthVerdict v = behind.isEmpty() ? HealthVerdict.OK : HealthVerdict.WARN;
    return new Check(
        "RULE_VERSION",
        "규칙 버전",
        v,
        "엔진 " + engine + " · 뒤처진 사용자 " + behind.size() + "명",
        behind.size(),
        v == HealthVerdict.OK ? null : RECALC,
        null,
        Map.of("engineRuleVersion", engine, "users", behind));
  }

  private static Check snapshotFreshness(DataHealthSnapshot data, YearMonth referenceMonth) {
    if (data.snapshots().isEmpty()) {
      return unknown("SNAPSHOT_FRESHNESS", "스냅샷 최신성", "사용자 스냅샷이 없습니다");
    }
    String current = YM.format(referenceMonth);
    List<String> stale = new ArrayList<>();
    data.snapshots().stream()
        .filter(s -> s.baseYearMonth() == null || s.baseYearMonth().compareTo(current) < 0)
        .forEach(s -> stale.add(prefix(s.userId())));
    HealthVerdict v = stale.isEmpty() ? HealthVerdict.OK : HealthVerdict.WARN;
    return new Check(
        "SNAPSHOT_FRESHNESS",
        "스냅샷 최신성",
        v,
        current + " 이전 스냅샷 사용자 " + stale.size() + "명",
        stale.size(),
        v == HealthVerdict.OK ? null : RECALC,
        null,
        Map.of("referenceMonth", current, "users", stale));
  }

  private static Check cpiLatest(
      DataHealthSnapshot data, YearMonth referenceMonth, HealthThresholds t) {
    if (data.cpiCodeCountByMonth().isEmpty()) {
      return unknown("CPI_LATEST", "CPI 최신월", "CPI 적재 기록이 없습니다");
    }
    String latest = new TreeSet<>(data.cpiCodeCountByMonth().keySet()).last();
    long lag = ChronoUnit.MONTHS.between(YearMonth.parse(latest, YM), referenceMonth);
    int delayed =
        (int) data.snapshots().stream().filter(s -> "UPDATE_DELAYED".equals(s.ppiStatus())).count();
    HealthVerdict v =
        lag > t.cpiMaxLagMonths()
            ? HealthVerdict.ERROR
            : lag == t.cpiMaxLagMonths() ? HealthVerdict.WARN : HealthVerdict.OK;
    String action =
        v == HealthVerdict.OK ? null : v == HealthVerdict.WARN ? "다음 달 이전에 CPI 적재" : "CPI 적재";
    return new Check(
        "CPI_LATEST",
        "CPI 최신월",
        v,
        "최신 CPI " + latest + " (" + lag + "개월 지연)",
        delayed,
        action,
        v == HealthVerdict.OK ? null : COLLECTOR,
        Map.of("latestCpiMonth", latest, "lagMonths", lag));
  }

  private static Check crawlFreshness(DataHealthSnapshot data, Instant now, HealthThresholds t) {
    if (data.crawlLatestBySource().isEmpty()) {
      return unknown("CRAWL_FRESHNESS", "정기 수집 최신성", "정기 수집 기록이 없습니다");
    }
    Map<String, Object> days = new LinkedHashMap<>();
    long worst = 0;
    for (Map.Entry<String, Instant> e : data.crawlLatestBySource().entrySet()) {
      long d = Duration.between(e.getValue(), now).toDays();
      days.put(e.getKey(), d);
      worst = Math.max(worst, d);
    }
    HealthVerdict v = worst > t.crawlStaleDays() ? HealthVerdict.WARN : HealthVerdict.OK;
    return new Check(
        "CRAWL_FRESHNESS",
        "정기 수집 최신성",
        v,
        "가장 오래된 원천 " + worst + "일 전",
        null,
        v == HealthVerdict.OK ? null : "정기 수집 실행",
        v == HealthVerdict.OK ? null : COLLECTOR,
        Map.of("daysSinceBySource", days));
  }

  /** 점으로 나눈 숫자를 차례로 비교한다. 숫자가 아닌 조각은 0 으로 본다. */
  static int compareVersions(String a, String b) {
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

  private static String prefix(String userId) {
    return userId == null ? "" : userId.substring(0, Math.min(8, userId.length()));
  }
}
