package com.carix.pipeline.health;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 점검에 필요한 데이터를 한 번에 읽은 묶음. 판정은 이 값만 보고 DB 를 다시 읽지 않는다. 금액은 담지 않는다. */
public record DataHealthSnapshot(
    Map<String, Integer> cpiCodeCountByMonth,
    Map<String, Integer> sampledByMonth,
    Map<String, Integer> cappedSampleByMonth,
    Map<String, Integer> aggregatedSampleByMonth,
    Map<String, Integer> familyPeerTotalsPreviousMonth,
    List<WageCell> wageCells,
    Map<String, Instant> crawlLatestBySource,
    List<UserSnapshot> snapshots,
    Map<String, String> familyByUser) {

  public DataHealthSnapshot {
    cpiCodeCountByMonth = cpiCodeCountByMonth == null ? Map.of() : Map.copyOf(cpiCodeCountByMonth);
    sampledByMonth = sampledByMonth == null ? Map.of() : Map.copyOf(sampledByMonth);
    cappedSampleByMonth = cappedSampleByMonth == null ? Map.of() : Map.copyOf(cappedSampleByMonth);
    aggregatedSampleByMonth =
        aggregatedSampleByMonth == null ? Map.of() : Map.copyOf(aggregatedSampleByMonth);
    familyPeerTotalsPreviousMonth =
        familyPeerTotalsPreviousMonth == null
            ? Map.of()
            : Map.copyOf(familyPeerTotalsPreviousMonth);
    wageCells = wageCells == null ? List.of() : List.copyOf(wageCells);
    crawlLatestBySource = crawlLatestBySource == null ? Map.of() : Map.copyOf(crawlLatestBySource);
    snapshots = snapshots == null ? List.of() : List.copyOf(snapshots);
    familyByUser = familyByUser == null ? Map.of() : Map.copyOf(familyByUser);
  }

  /** 최신 기준 연도 임금통계 한 칸. */
  public record WageCell(
      String basePeriod, String jobFamily, String experienceBracket, String source) {}

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
