package com.carix.pipeline.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.pipeline.dto.DataHealthReport;
import com.carix.pipeline.health.DataHealthSnapshot.SampleSeries;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WantedChecksTest {

  private static final YearMonth PREV = YearMonth.of(2026, 9);

  private static DataHealthReport.Group group(SampleSeries wanted) {
    var data = new DataHealthSnapshot(null, null, null, null, null, null, null, null, null, wanted);
    return WantedChecks.group(data, PREV, HealthThresholds.defaults());
  }

  private static DataHealthReport.Check check(DataHealthReport.Group g, String id) {
    return g.checks().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
  }

  @Test
  @DisplayName("지난달 원티드 표본 0 이면 문제, 목표의 경고 비율 미만이면 주의")
  void lastMonth_verdicts() {
    var zero = group(new SampleSeries(Map.of("202608", 250), Map.of(), Map.of()));
    assertThat(check(zero, "WANTED_SAMPLE_LAST_MONTH").verdict()).isEqualTo(HealthVerdict.ERROR);

    var low = group(new SampleSeries(Map.of("202609", 199), Map.of(), Map.of()));
    assertThat(check(low, "WANTED_SAMPLE_LAST_MONTH").verdict()).isEqualTo(HealthVerdict.WARN);

    var ok = group(new SampleSeries(Map.of("202609", 200), Map.of(), Map.of()));
    assertThat(check(ok, "WANTED_SAMPLE_LAST_MONTH").verdict()).isEqualTo(HealthVerdict.OK);
  }

  @Test
  @DisplayName("원티드 표본이 없으면 세 점검 모두 판정 불가")
  void noSamples_allUnknown() {
    var g = group(null);
    assertThat(g.id()).isEqualTo("WANTED");
    assertThat(g.checks())
        .extracting(DataHealthReport.Check::id)
        .containsExactly(
            "WANTED_SAMPLE_LAST_MONTH", "WANTED_SAMPLE_GAPS", "WANTED_AGGREGATE_STALE");
    assertThat(g.checks())
        .allSatisfy(c -> assertThat(c.verdict()).isEqualTo(HealthVerdict.UNKNOWN));
  }

  @Test
  @DisplayName("영향 사용자 수는 항상 null 이다")
  void impactUsers_alwaysNull() {
    var g = group(new SampleSeries(Map.of("202607", 250), Map.of("202607", 250), Map.of()));
    assertThat(g.checks()).allSatisfy(c -> assertThat(c.impactUsers()).isNull());
    assertThat(check(g, "WANTED_SAMPLE_GAPS").verdict()).isEqualTo(HealthVerdict.WARN);
    assertThat(check(g, "WANTED_AGGREGATE_STALE").verdict()).isEqualTo(HealthVerdict.ERROR);
    assertThat(List.of(g)).hasSize(1);
  }
}
