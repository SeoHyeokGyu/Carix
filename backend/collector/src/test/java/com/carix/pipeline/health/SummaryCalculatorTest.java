package com.carix.pipeline.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.pipeline.dto.DataHealthReport;
import com.carix.pipeline.health.DataHealthSnapshot.UserSnapshot;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SummaryCalculatorTest {

  private static DataHealthSnapshot with(
      List<UserSnapshot> snapshots, Map<String, String> families) {
    return new DataHealthSnapshot(null, null, null, null, null, null, null, snapshots, families);
  }

  private static UserSnapshot snap(
      String user, String ppi, String mvi, String signal, String... rules) {
    return new UserSnapshot(user, "202610", "1.3.0", signal, ppi, mvi, List.of(rules));
  }

  @Test
  @DisplayName("세 답이 온전한 사용자만 센다 — 비교 기간 짧음은 온전, 부분 판정은 아니다")
  void summary_countsFullyAnswered() {
    var data =
        with(
            List.of(
                snap("u1", "INSUFFICIENT_HISTORY", "OK", "STAY", "연봉이 시장 수준에 근접"),
                snap("u2", "OK", "INSUFFICIENT_SAMPLE", "STAY", "시장가치 미반영"),
                snap("u3", "OK", "OK", "STAY", "시장가치 불확실")),
            Map.of("u1", "DEVELOPER", "u2", "DEVELOPER", "u3", "DATA"));

    DataHealthReport.Summary s = SummaryCalculator.summarize(data, List.of());

    assertThat(s.users()).isEqualTo(3);
    assertThat(s.fullyAnswered()).isEqualTo(1);
    assertThat(s.gaps())
        .contains(
            new DataHealthReport.AnswerGap("MVI", "INSUFFICIENT_SAMPLE", 1),
            new DataHealthReport.AnswerGap("DECISION", "MVI_NOT_REFLECTED", 1),
            new DataHealthReport.AnswerGap("DECISION", "MVI_UNCERTAIN", 1));
  }

  @Test
  @DisplayName("스냅샷이 없는 사용자는 NO_SNAPSHOT 빈 답이다")
  void summary_countsUsersWithoutSnapshot() {
    var data = with(List.of(), Map.of("u1", "DEVELOPER"));

    DataHealthReport.Summary s = SummaryCalculator.summarize(data, List.of());

    assertThat(s.users()).isEqualTo(1);
    assertThat(s.fullyAnswered()).isZero();
    assertThat(s.gaps()).containsExactly(new DataHealthReport.AnswerGap("ALL", "NO_SNAPSHOT", 1));
  }

  @Test
  @DisplayName("지수 상태가 없는 옛 rationale 은 온전으로 세지 않는다")
  void summary_missingStatusesAreNotComplete() {
    var data = with(List.of(snap("u1", null, null, "STAY")), Map.of("u1", "DEVELOPER"));

    DataHealthReport.Summary s = SummaryCalculator.summarize(data, List.of());

    assertThat(s.fullyAnswered()).isZero();
    assertThat(s.gaps()).contains(new DataHealthReport.AnswerGap("PPI", "UNKNOWN_RATIONALE", 1));
  }

  @Test
  @DisplayName("판정 건수를 그룹 전체에서 센다")
  void summary_countsVerdicts() {
    var check =
        new DataHealthReport.Check("X", "x", HealthVerdict.WARN, "m", null, null, null, Map.of());
    var group = new DataHealthReport.Group("PPI", "구매력 지수", "내 실질 구매력은?", List.of(check, check));

    DataHealthReport.Summary s =
        SummaryCalculator.summarize(with(List.of(), Map.of()), List.of(group));

    assertThat(s.verdictCounts())
        .containsEntry(HealthVerdict.WARN, 2)
        .containsEntry(HealthVerdict.OK, 0);
  }
}
