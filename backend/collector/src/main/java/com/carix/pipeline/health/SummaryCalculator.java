package com.carix.pipeline.health;

import com.carix.pipeline.dto.DataHealthReport;
import com.carix.pipeline.health.DataHealthSnapshot.UserSnapshot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 맨 위 요약 — 「세 질문에 온전히 답하는 사용자」와 빈 답 사유. */
public final class SummaryCalculator {

  public static final String MVI_NOT_REFLECTED = "시장가치 미반영";
  public static final String MVI_UNCERTAIN = "시장가치 불확실";
  private static final Set<String> PPI_ANSWERED = Set.of("OK", "INSUFFICIENT_HISTORY");

  private SummaryCalculator() {}

  public static DataHealthReport.Summary summarize(DataHealthSnapshot data) {
    Map<String, UserSnapshot> byUser = new LinkedHashMap<>();
    data.snapshots().forEach(s -> byUser.put(s.userId(), s));
    java.util.Set<String> users = new java.util.LinkedHashSet<>(data.familyByUser().keySet());
    users.addAll(byUser.keySet());

    Map<List<String>, Integer> gapCounts = new LinkedHashMap<>();
    int fully = 0;
    for (String user : users) {
      UserSnapshot s = byUser.get(user);
      if (s == null) {
        gapCounts.merge(List.of("ALL", "NO_SNAPSHOT"), 1, Integer::sum);
        continue;
      }
      List<List<String>> gaps = gapsOf(s);
      if (gaps.isEmpty()) {
        fully++;
      }
      gaps.forEach(g -> gapCounts.merge(g, 1, Integer::sum));
    }

    List<DataHealthReport.AnswerGap> gaps = new ArrayList<>();
    gapCounts.forEach((k, v) -> gaps.add(new DataHealthReport.AnswerGap(k.get(0), k.get(1), v)));

    return new DataHealthReport.Summary(users.size(), fully, gaps);
  }

  private static List<List<String>> gapsOf(UserSnapshot s) {
    List<List<String>> gaps = new ArrayList<>();
    if (s.ppiStatus() == null) {
      gaps.add(List.of("PPI", "UNKNOWN_RATIONALE"));
    } else if (!PPI_ANSWERED.contains(s.ppiStatus())) {
      gaps.add(List.of("PPI", s.ppiStatus()));
    }
    if (s.mviStatus() == null) {
      gaps.add(List.of("MVI", "UNKNOWN_RATIONALE"));
    } else if (!"OK".equals(s.mviStatus())) {
      gaps.add(List.of("MVI", s.mviStatus()));
    }
    if ("INSUFFICIENT_DATA".equals(s.decisionSignal())) {
      gaps.add(List.of("DECISION", "INSUFFICIENT_DATA"));
    } else if (s.rulesApplied().contains(MVI_NOT_REFLECTED)) {
      gaps.add(List.of("DECISION", "MVI_NOT_REFLECTED"));
    } else if (s.rulesApplied().contains(MVI_UNCERTAIN)) {
      gaps.add(List.of("DECISION", "MVI_UNCERTAIN"));
    }
    return gaps;
  }
}
