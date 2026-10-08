package com.carix.pipeline.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.pipeline.dto.DataHealthReport.Check;
import com.carix.pipeline.health.DataHealthSnapshot.SampleSeries;
import com.carix.pipeline.health.DataHealthSnapshot.UserSnapshot;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HealthChecksTest {

  private static final YearMonth REF = YearMonth.of(2026, 10);
  private static final Instant NOW = Instant.parse("2026-10-08T00:00:00Z");
  private final HealthThresholds t = new HealthThresholds();

  private static DataHealthSnapshot snapshot(
      SampleSeries jk,
      SampleSeries wanted,
      Map<String, Integer> cpi,
      Map<String, Instant> crawl,
      List<UserSnapshot> users) {
    return new DataHealthSnapshot(cpi, jk, wanted, crawl, users, Map.of());
  }

  private static Check find(List<Check> checks, String id) {
    return checks.stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
  }

  private static UserSnapshot user(String id, String month, String version) {
    return new UserSnapshot(id, month, version, "STAY", "OK", "OK", List.of());
  }

  private static SampleSeries series(int capped, Integer aggregated) {
    return new SampleSeries(
        Map.of("202609", capped),
        Map.of("202609", capped),
        aggregated == null ? Map.of() : Map.of("202609", aggregated));
  }

  @Test
  @DisplayName("점검은 5개이고 순서가 고정이다")
  void fiveChecksInOrder() {
    List<Check> c =
        HealthChecks.all(
            snapshot(
                series(200, 200),
                series(200, 200),
                Map.of("202609", 12),
                Map.of("SARAMIN", NOW),
                List.of(user("u", "202610", "1.4.0"))),
            REF,
            "1.4.0",
            t,
            NOW);
    assertThat(c)
        .extracting(Check::id)
        .containsExactly(
            "AGGREGATE_STALE",
            "RULE_VERSION",
            "SNAPSHOT_FRESHNESS",
            "CPI_LATEST",
            "CRAWL_FRESHNESS");
    assertThat(c).allSatisfy(x -> assertThat(x.verdict()).isEqualTo(HealthVerdict.OK));
  }

  @Test
  @DisplayName("재집계 누락은 잡코리아면 오류, 원티드만이면 경고다")
  void aggregateStaleSeverityBySource() {
    var jkStale =
        HealthChecks.all(
            snapshot(series(200, 190), series(200, 200), Map.of(), Map.of(), List.of()),
            REF,
            "1.4.0",
            t,
            NOW);
    var wantedStale =
        HealthChecks.all(
            snapshot(series(200, 200), series(200, 190), Map.of(), Map.of(), List.of()),
            REF,
            "1.4.0",
            t,
            NOW);
    assertThat(find(jkStale, "AGGREGATE_STALE").verdict()).isEqualTo(HealthVerdict.ERROR);
    assertThat(find(wantedStale, "AGGREGATE_STALE").verdict()).isEqualTo(HealthVerdict.WARN);
    assertThat(find(jkStale, "AGGREGATE_STALE").evidence())
        .containsEntry("JOBKOREA", List.of("202609"))
        .containsEntry("WANTED", List.of());
  }

  @Test
  @DisplayName("두 원천 표본이 모두 비면 재집계 누락은 판정 불가")
  void aggregateStaleUnknownWhenBothEmpty() {
    var c =
        HealthChecks.all(snapshot(null, null, Map.of(), Map.of(), List.of()), REF, "1.4.0", t, NOW);
    assertThat(find(c, "AGGREGATE_STALE").verdict()).isEqualTo(HealthVerdict.UNKNOWN);
  }

  @Test
  @DisplayName("CPI 지연 1개월 OK, 2개월 경고, 3개월 오류, 없으면 판정 불가")
  void cpiLag() {
    assertThat(cpi(Map.of("202609", 12))).isEqualTo(HealthVerdict.OK);
    assertThat(cpi(Map.of("202608", 12))).isEqualTo(HealthVerdict.WARN);
    assertThat(cpi(Map.of("202607", 12))).isEqualTo(HealthVerdict.ERROR);
    assertThat(cpi(Map.of())).isEqualTo(HealthVerdict.UNKNOWN);
  }

  private HealthVerdict cpi(Map<String, Integer> cpi) {
    return find(
            HealthChecks.all(snapshot(null, null, cpi, Map.of(), List.of()), REF, "1.4.0", t, NOW),
            "CPI_LATEST")
        .verdict();
  }

  @Test
  @DisplayName("수집 신선도는 35일까지 OK, 넘으면 경고")
  void crawlFreshness() {
    Instant d35 = NOW.minusSeconds(35L * 86400);
    Instant d36 = NOW.minusSeconds(36L * 86400);
    assertThat(crawl(Map.of("WANTED", d35))).isEqualTo(HealthVerdict.OK);
    assertThat(crawl(Map.of("WANTED", d36))).isEqualTo(HealthVerdict.WARN);
  }

  @Test
  @DisplayName("정기 수집 기록이 없으면 수집 신선도는 판정 불가")
  void crawlFreshnessUnknownWhenNoRecords() {
    assertThat(crawl(Map.of())).isEqualTo(HealthVerdict.UNKNOWN);
  }

  private HealthVerdict crawl(Map<String, Instant> crawl) {
    return find(
            HealthChecks.all(
                snapshot(null, null, Map.of(), crawl, List.of()), REF, "1.4.0", t, NOW),
            "CRAWL_FRESHNESS")
        .verdict();
  }

  @Test
  @DisplayName("규칙 버전: 섞이면 경고, 엔진 버전을 못 받으면 판정 불가")
  void ruleVersion() {
    var users = List.of(user("a", "202610", "1.4.0"), user("b", "202610", "1.3.0"));
    var data = snapshot(null, null, Map.of(), Map.of(), users);
    assertThat(find(HealthChecks.all(data, REF, "1.4.0", t, NOW), "RULE_VERSION").verdict())
        .isEqualTo(HealthVerdict.WARN);
    assertThat(find(HealthChecks.all(data, REF, null, t, NOW), "RULE_VERSION").verdict())
        .isEqualTo(HealthVerdict.UNKNOWN);
  }

  @Test
  @DisplayName("스냅샷이 기준월 이전이면 경고")
  void snapshotFreshness() {
    var stale = List.of(user("a", "202609", "1.4.0"));
    var data = snapshot(null, null, Map.of(), Map.of(), stale);
    assertThat(find(HealthChecks.all(data, REF, "1.4.0", t, NOW), "SNAPSHOT_FRESHNESS").verdict())
        .isEqualTo(HealthVerdict.WARN);
  }
}
