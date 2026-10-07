package com.carix.pipeline.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.pipeline.dto.DataHealthReport;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MviChecksTest {

  private static final YearMonth PREV = YearMonth.of(2026, 9);

  private static DataHealthReport.Check check(
      Map<String, Integer> sampled,
      Map<String, Integer> capped,
      Map<String, Integer> aggregated,
      Map<String, Integer> families,
      Map<String, String> familyByUser,
      String id) {
    var data =
        new DataHealthSnapshot(
            null, sampled, capped, aggregated, families, null, null, List.of(), familyByUser, null);
    return MviChecks.group(data, PREV, HealthThresholds.defaults()).checks().stream()
        .filter(c -> c.id().equals(id))
        .findFirst()
        .orElseThrow();
  }

  @Test
  @DisplayName("지난달 표본 0 문제, 199 주의, 250 정상")
  void sampleLastMonth_boundaries() {
    assertThat(check(Map.of("202608", 250), null, null, null, null, "SAMPLE_LAST_MONTH").verdict())
        .isEqualTo(HealthVerdict.ERROR);
    assertThat(check(Map.of("202609", 199), null, null, null, null, "SAMPLE_LAST_MONTH").verdict())
        .isEqualTo(HealthVerdict.WARN);
    assertThat(check(Map.of("202609", 250), null, null, null, null, "SAMPLE_LAST_MONTH").verdict())
        .isEqualTo(HealthVerdict.OK);
  }

  @Test
  @DisplayName("첫 표본 달 이후 비어 있는 달은 주의")
  void sampleGaps_missingMonth() {
    var c =
        check(
            Map.of("202606", 200, "202608", 250, "202609", 250),
            null,
            null,
            null,
            null,
            "SAMPLE_GAPS");
    assertThat(c.verdict()).isEqualTo(HealthVerdict.WARN);
    assertThat(c.evidence()).containsEntry("gapMonths", List.of("202607"));
  }

  @Test
  @DisplayName("집계 표본 수가 상한 적용 표본 수와 다르면 재집계 필요")
  void aggregateStale_mismatch() {
    var stale =
        check(
            Map.of("202609", 250),
            Map.of("202609", 240),
            Map.of("202609", 200),
            null,
            null,
            "AGGREGATE_STALE");
    var fresh =
        check(
            Map.of("202609", 250),
            Map.of("202609", 240),
            Map.of("202609", 240),
            null,
            null,
            "AGGREGATE_STALE");
    var missing =
        check(
            Map.of("202609", 250), Map.of("202609", 240), Map.of(), null, null, "AGGREGATE_STALE");
    assertThat(stale.verdict()).isEqualTo(HealthVerdict.ERROR);
    assertThat(fresh.verdict()).isEqualTo(HealthVerdict.OK);
    assertThat(missing.verdict()).isEqualTo(HealthVerdict.ERROR);
  }

  @Test
  @DisplayName("상한 적용 표본이 없으면 재집계 누락 점검 판정 불가")
  void aggregateStale_cappedMissing_returnsUnknown() {
    var noCapped = check(Map.of("202609", 250), Map.of(), Map.of(), null, null, "AGGREGATE_STALE");
    assertThat(noCapped.verdict()).isEqualTo(HealthVerdict.UNKNOWN);
  }

  @Test
  @DisplayName("표본 달 중 상한 적용이 누락되면 판정 불가")
  void aggregateStale_missingCappedForSampledMonth_returnsUnknown() {
    var partialCapped =
        check(
            Map.of("202608", 250, "202609", 250),
            Map.of("202609", 240),
            Map.of("202609", 240),
            null,
            null,
            "AGGREGATE_STALE");
    assertThat(partialCapped.verdict()).isEqualTo(HealthVerdict.UNKNOWN);
  }

  @Test
  @DisplayName("직무 표본 10 미만이면 주의이고 그 직무 사용자를 영향으로 센다")
  void peerSampleByFamily_thinFamily() {
    var c =
        check(
            Map.of("202609", 250),
            null,
            null,
            Map.of("DEVELOPER", 180, "DATA", 9, "AI", 30),
            Map.of("u1", "DATA", "u2", "DEVELOPER"),
            "PEER_SAMPLE_BY_FAMILY");
    assertThat(c.verdict()).isEqualTo(HealthVerdict.WARN);
    assertThat(c.impactUsers()).isEqualTo(1);
    assertThat(c.evidence()).containsEntry("thinFamilies", List.of("DATA"));
  }

  @Test
  @DisplayName("표본이 하나도 없으면 표본·집계 점검은 판정 불가")
  void unknownWithoutSamples() {
    assertThat(check(Map.of(), null, null, null, null, "SAMPLE_LAST_MONTH").verdict())
        .isEqualTo(HealthVerdict.UNKNOWN);
    assertThat(check(Map.of(), null, null, null, null, "AGGREGATE_STALE").verdict())
        .isEqualTo(HealthVerdict.UNKNOWN);
  }
}
