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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DataHealthServiceTest {

  private static Clock at(String instant) {
    return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
  }

  private static DataHealthSnapshot snap(DataHealthSnapshot.SampleSeries jk) {
    return new DataHealthSnapshot(Map.of("202609", 12), jk, null, Map.of(), List.of(), Map.of());
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
  @DisplayName("고정 Clock 이 서울 11-01 01:00 이어도 referenceMonth 는 202610")
  void evaluate_referenceMonthUtc() {
    var service =
        new DataHealthService(
            () -> snap(null),
            () -> Optional.of("1.3.0"),
            HealthThresholds.defaults(),
            at("2026-10-31T16:00:00Z"));
    assertThat(service.evaluate().referenceMonth()).isEqualTo("202610");
  }

  @Test
  @DisplayName("점검 다섯을 정해진 순서로 내고 groups 는 없다")
  void evaluate_checksInOrder() {
    DataHealthReader reader = () -> snap(null);
    var service =
        new DataHealthService(
            reader,
            () -> Optional.of("1.3.0"),
            HealthThresholds.defaults(),
            at("2026-10-05T00:00:00Z"));

    DataHealthReport r = service.evaluate();

    assertThat(r.referenceMonth()).isEqualTo("202610");
    assertThat(r.previousMonth()).isEqualTo("202609");
    assertThat(r.engineRuleVersion()).isEqualTo("1.3.0");
    assertThat(r.checks())
        .extracting(DataHealthReport.Check::id)
        .containsExactly(
            "AGGREGATE_STALE",
            "RULE_VERSION",
            "SNAPSHOT_FRESHNESS",
            "CPI_LATEST",
            "CRAWL_FRESHNESS");
    assertThat(DataHealthReport.class.getRecordComponents())
        .extracting(c -> c.getName())
        .doesNotContain("groups");
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

  @Test
  @DisplayName("CPI·수집 신선도 링크, 나머지는 null")
  void drillDown_others() {
    var r = evaluate(snap(null));
    assertThat(check(r, "CPI_LATEST").drillDown()).isEqualTo("/admin/data?tab=cpi");
    assertThat(check(r, "CRAWL_FRESHNESS").drillDown()).isNull();
    assertThat(check(r, "RULE_VERSION").drillDown()).isNull();
    assertThat(check(r, "SNAPSHOT_FRESHNESS").drillDown()).isNull();

    var withCrawl =
        evaluate(
            new DataHealthSnapshot(
                Map.of(),
                null,
                null,
                Map.of(
                    "JOBKOREA", Instant.parse("2026-10-01T00:00:00Z"),
                    "WANTED", Instant.parse("2026-09-01T00:00:00Z")),
                List.of(),
                Map.of()));
    assertThat(check(withCrawl, "CRAWL_FRESHNESS").drillDown())
        .isEqualTo("/admin/data?tab=postings&month=202609&sort=recent&source=WANTED");
  }

  @Test
  @DisplayName("재집계 누락 링크: 해석 못 하는 달 키는 건너뛰고 모두 못 하면 기본 범위")
  void drillDown_aggregateStale_malformedMonth() {
    var withBad =
        new DataHealthReport.Check(
            "AGGREGATE_STALE",
            "t",
            HealthVerdict.ERROR,
            "m",
            null,
            null,
            null,
            Map.of("JOBKOREA", List.of("bad", "202401"), "WANTED", List.of()));
    assertThat(DataHealthService.drillDown(withBad, YearMonth.of(2026, 10)))
        .isEqualTo("/admin/data?tab=monthly&from=202401&to=202610");
    var allBad =
        new DataHealthReport.Check(
            "AGGREGATE_STALE",
            "t",
            HealthVerdict.ERROR,
            "m",
            null,
            null,
            null,
            Map.of("JOBKOREA", List.of("x", "2024-1")));
    assertThat(DataHealthService.drillDown(allBad, YearMonth.of(2026, 10)))
        .isEqualTo("/admin/data?tab=monthly&from=202505&to=202610");
  }

  @Test
  @DisplayName("점검 계산이 예외를 내면 다섯 점검 모두 판정 불가")
  void checksThrowing_allUnknown() {
    var throwing =
        new HealthThresholds() {
          @Override
          public int cpiMaxLagMonths() {
            throw new IllegalStateException("boom");
          }
        };
    var service =
        new DataHealthService(
            () -> snap(null), Optional::empty, throwing, at("2026-10-05T00:00:00Z"));
    DataHealthReport r = service.evaluate();
    assertThat(r.checks()).hasSize(5);
    assertThat(r.checks())
        .allSatisfy(c -> assertThat(c.verdict()).isEqualTo(HealthVerdict.UNKNOWN));
  }
}
