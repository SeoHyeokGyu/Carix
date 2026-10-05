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

  @Test
  @DisplayName("현재월은 서울 기준이다 — UTC 9월 30일 15시는 서울 10월 1일")
  void referenceMonths_useSeoulZone() {
    assertThat(DataHealthService.referenceMonth(at("2026-09-30T15:00:00Z")))
        .isEqualTo(YearMonth.of(2026, 10));
    assertThat(DataHealthService.referenceMonth(at("2026-09-30T14:59:59Z")))
        .isEqualTo(YearMonth.of(2026, 9));
  }

  @Test
  @DisplayName("그룹 넷을 정해진 순서로 내고 지난달을 읽기에 넘긴다")
  void evaluate_groupsInOrder() {
    YearMonth[] asked = new YearMonth[1];
    DataHealthReader reader =
        prev -> {
          asked[0] = prev;
          return new DataHealthSnapshot(
              Map.of("202609", 12),
              Map.of("202609", 250),
              null,
              null,
              null,
              null,
              null,
              List.of(),
              Map.of());
        };
    var service =
        new DataHealthService(
            reader,
            () -> Optional.of("1.3.0"),
            HealthThresholds.defaults(),
            at("2026-10-05T00:00:00Z"));

    DataHealthReport r = service.evaluate();

    assertThat(asked[0]).isEqualTo(YearMonth.of(2026, 9));
    assertThat(r.referenceMonth()).isEqualTo("202610");
    assertThat(r.previousMonth()).isEqualTo("202609");
    assertThat(r.engineRuleVersion()).isEqualTo("1.3.0");
    assertThat(r.groups())
        .extracting(DataHealthReport.Group::id)
        .containsExactly("PPI", "MVI", "DECISION", "SUPPORT");
  }

  @Test
  @DisplayName("한 그룹 계산이 예외를 내면 그 그룹 점검만 판정 불가이고 나머지는 계산된다")
  void service_oneFailingCheckBecomesUnknown() {
    DataHealthReader reader =
        prev -> {
          throw new IllegalStateException("db down");
        };
    var service =
        new DataHealthService(
            reader, Optional::empty, HealthThresholds.defaults(), at("2026-10-05T00:00:00Z"));

    DataHealthReport r = service.evaluate();

    assertThat(r.groups()).hasSize(4);
    assertThat(r.groups())
        .allSatisfy(
            g ->
                assertThat(g.checks())
                    .allSatisfy(c -> assertThat(c.verdict()).isEqualTo(HealthVerdict.UNKNOWN)));
    assertThat(r.engineRuleVersion()).isNull();
  }

  @Test
  @DisplayName("점검마다 원본 화면 링크를 판정과 무관하게 채운다")
  void drillDownLinks() {
    assertThat(DataHealthService.drillDown("SAMPLE_LAST_MONTH", "202609"))
        .isEqualTo("/admin/data?tab=postings&month=202609&sampled=true");
    String monthly = "/admin/data?tab=monthly&from=202310&to=202609";
    for (String id :
        List.of("SAMPLE_GAPS", "AGGREGATE_STALE", "PEER_SAMPLE_BY_FAMILY", "CRAWL_FRESHNESS")) {
      assertThat(DataHealthService.drillDown(id, "202609")).isEqualTo(monthly);
    }
    String cpi = "/admin/data?tab=cpi&from=201610&to=202609";
    for (String id : List.of("CPI_LATEST", "CPI_GAP")) {
      assertThat(DataHealthService.drillDown(id, "202609")).isEqualTo(cpi);
    }
    assertThat(DataHealthService.drillDown("WAGE_BASELINE", "202609"))
        .isEqualTo("/admin/data?tab=wage");
    assertThat(DataHealthService.drillDown("RULE_VERSION", "202609")).isNull();

    DataHealthReader reader =
        prev ->
            new DataHealthSnapshot(
                Map.of("202609", 12),
                Map.of("202609", 250),
                null,
                null,
                null,
                null,
                null,
                List.of(),
                Map.of());
    var service =
        new DataHealthService(
            reader,
            () -> Optional.of("1.3.0"),
            HealthThresholds.defaults(),
            at("2026-10-05T00:00:00Z"));
    var check =
        service.evaluate().groups().stream()
            .flatMap(g -> g.checks().stream())
            .filter(c -> c.id().equals("SAMPLE_LAST_MONTH"))
            .findFirst()
            .orElseThrow();
    assertThat(check.drillDown()).isEqualTo("/admin/data?tab=postings&month=202609&sampled=true");
  }
}
