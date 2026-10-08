package com.carix.pipeline.health;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 점검에 필요한 데이터를 한 번에 읽은 묶음. 판정은 이 값만 보고 DB 를 다시 읽지 않는다. 금액은 담지 않는다. */
public record DataHealthSnapshot(
    Map<String, Integer> cpiCodeCountByMonth,
    SampleSeries jobKorea,
    SampleSeries wanted,
    Map<String, Instant> crawlLatestBySource,
    List<UserSnapshot> snapshots,
    Map<String, String> familyByUser) {

  public DataHealthSnapshot {
    cpiCodeCountByMonth = cpiCodeCountByMonth == null ? Map.of() : Map.copyOf(cpiCodeCountByMonth);
    jobKorea = jobKorea == null ? new SampleSeries(null, null, null) : jobKorea;
    wanted = wanted == null ? new SampleSeries(null, null, null) : wanted;
    crawlLatestBySource = crawlLatestBySource == null ? Map.of() : Map.copyOf(crawlLatestBySource);
    snapshots = snapshots == null ? List.of() : List.copyOf(snapshots);
    familyByUser = familyByUser == null ? Map.of() : Map.copyOf(familyByUser);
  }

  /** 한 원천의 월별 표본 수·상한 적용 표본 수·집계 건수. */
  public record SampleSeries(
      Map<String, Integer> sampledByMonth,
      Map<String, Integer> cappedByMonth,
      Map<String, Integer> aggregatedByMonth) {

    public SampleSeries {
      sampledByMonth = sampledByMonth == null ? Map.of() : Map.copyOf(sampledByMonth);
      cappedByMonth = cappedByMonth == null ? Map.of() : Map.copyOf(cappedByMonth);
      aggregatedByMonth = aggregatedByMonth == null ? Map.of() : Map.copyOf(aggregatedByMonth);
    }
  }

  /** 사용자별 최신 스냅샷. 상태값은 rationale 에 없으면 {@code null}. */
  public record UserSnapshot(
      String userId,
      String baseYearMonth,
      String ruleVersion,
      String decisionSignal,
      String ppiStatus,
      String mviStatus,
      List<String> rulesApplied) {

    public UserSnapshot {
      rulesApplied = rulesApplied == null ? List.of() : List.copyOf(rulesApplied);
    }
  }
}
