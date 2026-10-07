package com.carix.pipeline.health;

import com.carix.pipeline.dto.DataHealthReport;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** ② 시장가치 지수 — 「내 스킬 가치는?」. 잡코리아 무작위 표본(sampled_month)과 그 집계를 본다. */
public final class MviChecks {

  public static final List<String> TARGET_FAMILIES = List.of("DEVELOPER", "DATA", "AI");
  private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");
  private static final String PIPELINE = "/admin/collector";

  private MviChecks() {}

  public static DataHealthReport.Group group(
      DataHealthSnapshot data, YearMonth previousMonth, HealthThresholds t) {
    int impactUsers = mviNotOk(data);
    return new DataHealthReport.Group(
        "MVI",
        "시장가치 지수",
        "내 스킬 가치는?",
        List.of(
            lastMonth(data.jobKorea(), previousMonth, t, "", impactUsers),
            gaps(data.jobKorea(), previousMonth, ""),
            aggregateStale(data.jobKorea(), "", impactUsers),
            peerByFamily(data, t)));
  }

  private static int mviNotOk(DataHealthSnapshot data) {
    return (int) data.snapshots().stream().filter(s -> !"OK".equals(s.mviStatus())).count();
  }

  /** {@code idPrefix} 는 점검 id 앞에 붙는다(MVI 는 빈 문자열). {@code impactUsers} 는 문제일 때만 실린다. */
  static DataHealthReport.Check lastMonth(
      DataHealthSnapshot.SampleSeries data,
      YearMonth previousMonth,
      HealthThresholds t,
      String idPrefix,
      Integer impactUsers) {
    if (data.sampledByMonth().isEmpty()) {
      return PpiChecks.unknown(idPrefix + "SAMPLE_LAST_MONTH", "지난달 표본", "표본이 하나도 없습니다");
    }
    String key = YM.format(previousMonth);
    int count = data.sampledByMonth().getOrDefault(key, 0);
    int warnBelow = (int) Math.round(t.sampleTarget() * t.sampleWarnRatio());
    HealthVerdict v =
        count == 0
            ? HealthVerdict.ERROR
            : count < warnBelow ? HealthVerdict.WARN : HealthVerdict.OK;
    return new DataHealthReport.Check(
        idPrefix + "SAMPLE_LAST_MONTH",
        "지난달 표본",
        v,
        key + " 표본 " + count + "건 (목표 " + t.sampleTarget() + ")",
        v == HealthVerdict.OK ? (impactUsers == null ? null : 0) : impactUsers,
        v == HealthVerdict.OK ? null : key + " 백필",
        v == HealthVerdict.OK ? null : PIPELINE,
        Map.of(
            "month",
            key,
            "sampled",
            count,
            "byMonth",
            new LinkedHashMap<>(sorted(data.sampledByMonth()))));
  }

  static DataHealthReport.Check gaps(
      DataHealthSnapshot.SampleSeries data, YearMonth previousMonth, String idPrefix) {
    if (data.sampledByMonth().isEmpty()) {
      return PpiChecks.unknown(idPrefix + "SAMPLE_GAPS", "표본 공백 달", "표본이 하나도 없습니다");
    }
    String first = new TreeSet<>(data.sampledByMonth().keySet()).first();
    List<String> gapMonths = new ArrayList<>();
    for (YearMonth m = YearMonth.parse(first, YM); !m.isAfter(previousMonth); m = m.plusMonths(1)) {
      if (data.sampledByMonth().getOrDefault(YM.format(m), 0) == 0) {
        gapMonths.add(YM.format(m));
      }
    }
    HealthVerdict v = gapMonths.isEmpty() ? HealthVerdict.OK : HealthVerdict.WARN;
    return new DataHealthReport.Check(
        idPrefix + "SAMPLE_GAPS",
        "표본 공백 달",
        v,
        gapMonths.isEmpty() ? "공백 없음 (" + first + "부터)" : "공백 " + gapMonths.size() + "개월",
        null,
        v == HealthVerdict.OK ? null : "공백 월 백필",
        v == HealthVerdict.OK ? null : PIPELINE,
        Map.of("firstSampleMonth", first, "gapMonths", gapMonths));
  }

  static DataHealthReport.Check aggregateStale(
      DataHealthSnapshot.SampleSeries data, String idPrefix, Integer impactUsers) {
    if (data.sampledByMonth().isEmpty()) {
      return PpiChecks.unknown(idPrefix + "AGGREGATE_STALE", "재집계 누락", "표본이 하나도 없습니다");
    }
    // Union of all three maps
    TreeSet<String> allMonths = new TreeSet<>();
    allMonths.addAll(data.sampledByMonth().keySet());
    allMonths.addAll(data.cappedByMonth().keySet());
    allMonths.addAll(data.aggregatedByMonth().keySet());

    List<Map<String, Object>> rows = new ArrayList<>();
    List<String> stale = new ArrayList<>();
    for (String month : allMonths) {
      Integer capped = data.cappedByMonth().get(month);
      if (capped == null) {
        // Sampled but capped not calculated
        return PpiChecks.unknown(idPrefix + "AGGREGATE_STALE", "재집계 누락", "상한 적용 표본을 계산하지 못했습니다");
      }
      Integer aggregated = data.aggregatedByMonth().get(month);
      var row = new LinkedHashMap<String, Object>();
      row.put("month", month);
      row.put("capped", capped);
      if (aggregated != null) {
        row.put("aggregated", aggregated);
      }
      rows.add(row);
      if (aggregated == null || !aggregated.equals(capped)) {
        stale.add(month);
      }
    }
    HealthVerdict v = stale.isEmpty() ? HealthVerdict.OK : HealthVerdict.ERROR;
    return new DataHealthReport.Check(
        idPrefix + "AGGREGATE_STALE",
        "재집계 누락",
        v,
        stale.isEmpty() ? "표본과 집계 일치" : "불일치 " + stale.size() + "개월",
        v == HealthVerdict.OK ? (impactUsers == null ? null : 0) : impactUsers,
        v == HealthVerdict.OK ? null : "재집계",
        v == HealthVerdict.OK ? null : PIPELINE,
        Map.of("staleMonths", stale, "rows", rows));
  }

  private static DataHealthReport.Check peerByFamily(DataHealthSnapshot data, HealthThresholds t) {
    List<String> thin = new ArrayList<>();
    Map<String, Object> totals = new LinkedHashMap<>();
    for (String family : TARGET_FAMILIES) {
      int n = data.familyPeerTotalsPreviousMonth().getOrDefault(family, 0);
      totals.put(family, n);
      if (n < t.peerMinSample()) {
        thin.add(family);
      }
    }
    int impact = (int) data.familyByUser().values().stream().filter(thin::contains).count();
    HealthVerdict v = thin.isEmpty() ? HealthVerdict.OK : HealthVerdict.WARN;
    return new DataHealthReport.Check(
        "PEER_SAMPLE_BY_FAMILY",
        "직무별 동료집단 표본",
        v,
        thin.isEmpty() ? "모든 직무 " + t.peerMinSample() + "건 이상" : String.join("·", thin) + " 표본 부족",
        impact,
        v == HealthVerdict.OK ? null : "후속 과제: 얇은 직무 동료집단 판정을 최근 3개월 합으로",
        null,
        Map.of("totals", totals, "thinFamilies", thin));
  }

  private static Map<String, Integer> sorted(Map<String, Integer> m) {
    Map<String, Integer> out = new LinkedHashMap<>();
    new TreeSet<>(m.keySet()).forEach(k -> out.put(k, m.get(k)));
    return out;
  }
}
