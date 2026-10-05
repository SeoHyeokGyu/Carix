package com.carix.pipeline.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.pipeline.dto.DataHealthReport;
import com.carix.pipeline.health.DataHealthSnapshot.UserSnapshot;
import com.carix.pipeline.health.DataHealthSnapshot.WageCell;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DecisionChecksTest {

  private static final YearMonth NOW = YearMonth.of(2026, 10);

  private static List<WageCell> fullWage(String source) {
    List<WageCell> cells = new ArrayList<>();
    for (String f : MviChecks.TARGET_FAMILIES) {
      for (String b : DecisionChecks.BRACKETS) {
        cells.add(new WageCell("2025", f, b, source));
      }
    }
    return cells;
  }

  private static DataHealthReport.Check check(
      List<WageCell> wage, List<UserSnapshot> snaps, String engine, String id) {
    var data = new DataHealthSnapshot(null, null, null, null, null, wage, null, snaps, null);
    return DecisionChecks.group(data, NOW, engine).checks().stream()
        .filter(c -> c.id().equals(id))
        .findFirst()
        .orElseThrow();
  }

  private static UserSnapshot snap(String user, String month, String version) {
    return new UserSnapshot(user, month, version, "STAY", "OK", "OK", List.of());
  }

  @Test
  @DisplayName("임금통계가 전부 KOSIS 실측이고 칸이 다 있으면 정상, 폴백 행이나 빈 칸이 있으면 주의")
  void wageBaseline() {
    assertThat(check(fullWage("KOSIS"), List.of(), "1.3.0", "WAGE_BASELINE").verdict())
        .isEqualTo(HealthVerdict.OK);
    assertThat(check(fullWage("FALLBACK"), List.of(), "1.3.0", "WAGE_BASELINE").verdict())
        .isEqualTo(HealthVerdict.WARN);
    List<WageCell> missing = fullWage("KOSIS");
    missing.removeFirst();
    assertThat(check(missing, List.of(), "1.3.0", "WAGE_BASELINE").verdict())
        .isEqualTo(HealthVerdict.WARN);
    assertThat(check(List.of(), List.of(), "1.3.0", "WAGE_BASELINE").verdict())
        .isEqualTo(HealthVerdict.UNKNOWN);
  }

  @Test
  @DisplayName("엔진보다 낮은 규칙 버전 사용자가 있으면 주의 — 1.10.0 은 1.3.0 보다 높다")
  void ruleVersion() {
    var c =
        check(
            List.of(),
            List.of(snap("u1", "202610", "1.2.0"), snap("u2", "202610", "1.3.0")),
            "1.3.0",
            "RULE_VERSION");
    assertThat(c.verdict()).isEqualTo(HealthVerdict.WARN);
    assertThat(c.impactUsers()).isEqualTo(1);
    assertThat(DecisionChecks.compareVersions("1.10.0", "1.3.0")).isPositive();
  }

  @Test
  @DisplayName("엔진 버전을 못 받으면 규칙 버전은 판정 불가")
  void ruleVersion_unknownWhenEngineVersionMissing() {
    assertThat(
            check(List.of(), List.of(snap("u1", "202610", "1.2.0")), null, "RULE_VERSION")
                .verdict())
        .isEqualTo(HealthVerdict.UNKNOWN);
  }

  @Test
  @DisplayName("최신 스냅샷 기준월이 현재월보다 이전인 사용자가 있으면 주의")
  void snapshotFreshness() {
    var c =
        check(
            List.of(),
            List.of(snap("u1", "202609", "1.3.0"), snap("u2", "202610", "1.3.0")),
            "1.3.0",
            "SNAPSHOT_FRESHNESS");
    assertThat(c.verdict()).isEqualTo(HealthVerdict.WARN);
    assertThat(c.impactUsers()).isEqualTo(1);
  }

  @Test
  @DisplayName("판정 품질은 정보 표시라 늘 정상이고 사유별 사용자 수를 근거에 담는다")
  void decisionQuality() {
    var partial =
        new UserSnapshot("u1", "202610", "1.3.0", "STAY", "OK", "OK", List.of("시장가치 불확실"));
    var c = check(List.of(), List.of(partial), "1.3.0", "DECISION_QUALITY");
    assertThat(c.verdict()).isEqualTo(HealthVerdict.OK);
    assertThat(c.evidence()).containsKey("byReason");
  }

  @Test
  @DisplayName("스냅샷이 없으면 규칙 버전·최신성 점검을 판정 불가로 낸다")
  void emptySnapshots() {
    assertThat(check(List.of(), List.of(), "1.3.0", "RULE_VERSION").verdict())
        .isEqualTo(HealthVerdict.UNKNOWN);
    assertThat(check(List.of(), List.of(), "1.3.0", "SNAPSHOT_FRESHNESS").verdict())
        .isEqualTo(HealthVerdict.UNKNOWN);
  }

  @Test
  @DisplayName("사용자 ID 8자 초과는 첫 8자까지만 근거에 담는다")
  void userIdPrefix() {
    var c =
        check(
            List.of(),
            List.of(snap("6fa4914e-8cf1-4917", "202610", "1.2.0")),
            "1.3.0",
            "RULE_VERSION");
    assertThat(c.verdict()).isEqualTo(HealthVerdict.WARN);
    @SuppressWarnings("unchecked")
    List<String> users = (List<String>) c.evidence().get("users");
    assertThat(users).contains("6fa4914e");
  }
}
