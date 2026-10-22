package com.carix.pipeline.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.pipeline.dto.DataHealthReport;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DataHealthServiceTest {

  private static Clock at(String instant) {
    return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
  }

  private static DataHealthSnapshot snap(DataHealthSnapshot.SampleSeries jk) {
    return new DataHealthSnapshot(Set.of("202609"), jk, null, Map.of(), List.of(), Set.of());
  }

  @Test
  @DisplayName("현재월은 UTC 기준이다 — 서울 11월 1일 01시(UTC 10월 31일 16시)는 10월")
  void referenceMonth_usesUtc() {
    assertThat(DataHealthService.referenceMonth(at("2026-10-31T16:00:00Z")))
        .isEqualTo(YearMonth.of(2026, 10));
    assertThat(DataHealthService.referenceMonth(at("2026-11-01T00:00:00Z")))
        .isEqualTo(YearMonth.of(2026, 11));
  }

  @Test
  @DisplayName("읽기가 예외를 내면 다섯 점검 모두 판정 불가")
  void service_readFailureMakesAllUnknown() {
    DataHealthReader reader =
        () -> {
          throw new IllegalStateException("db down");
        };
    var service =
        new DataHealthService(
            reader, Optional::empty, HealthThresholds.defaults(), at("2026-10-05T00:00:00Z"));

    DataHealthReport r = service.evaluate();

    assertThat(r.checks()).hasSize(5);
    assertThat(r.checks())
        .allSatisfy(c -> assertThat(c.verdict()).isEqualTo(HealthVerdict.UNKNOWN));
    assertThat(r.engineRuleVersion()).isNull();
    assertThat(r.summary()).isNull();
  }

  private static DataHealthReport.Check check(DataHealthReport r, String id) {
    return r.checks().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
  }

  private static DataHealthReport evaluate(DataHealthSnapshot data) {
    return new DataHealthService(
            () -> data,
            () -> Optional.of("1.3.0"),
            HealthThresholds.defaults(),
            at("2026-10-05T00:00:00Z"))
        .evaluate();
  }

  @Test
  @DisplayName("재집계 누락 링크: 불일치가 없으면 기준월-17개월부터")
  void drillDown_aggregateStale_default() {
    var r =
        evaluate(
            snap(
                new DataHealthSnapshot.SampleSeries(
                    Map.of("202609", 5), Map.of("202609", 5), Map.of("202609", 5))));
    assertThat(check(r, "AGGREGATE_STALE").drillDown())
        .isEqualTo("/admin/data?tab=monthly&from=202505&to=202610");
  }

  @Test
  @DisplayName("재집계 누락 링크: 불일치 202401 이 있으면 from=202401")
  void drillDown_aggregateStale_earliestMismatch() {
    var r =
        evaluate(
            snap(
                new DataHealthSnapshot.SampleSeries(
                    Map.of("202401", 5, "202609", 5),
                    Map.of("202401", 5, "202609", 5),
                    Map.of("202609", 5))));
    assertThat(check(r, "AGGREGATE_STALE").drillDown())
        .isEqualTo("/admin/data?tab=monthly&from=202401&to=202610");
  }

  @Test
  @DisplayName("재집계 누락 링크: 기준월-35개월보다 이르면 202311 로 자른다")
  void drillDown_aggregateStale_clamped() {
    var r =
        evaluate(
            snap(
                new DataHealthSnapshot.SampleSeries(
                    Map.of("202001", 5), Map.of("202001", 5), Map.of())));
    assertThat(check(r, "AGGREGATE_STALE").drillDown())
        .isEqualTo("/admin/data?tab=monthly&from=202311&to=202610");
  }
}
