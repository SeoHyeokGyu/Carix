package com.carix.pipeline.explore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.carix.cpi.CpiObservation;
import com.carix.cpi.CpiObservationRepository;
import com.carix.jobposting.JobPostingRepository;
import com.carix.market.MarketAggregateService;
import com.carix.pipeline.dto.explore.CpiTable;
import com.carix.pipeline.dto.explore.WageTable;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class PurchasingPowerExploreTest {

  @Autowired private CpiObservationRepository cpiRepository;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private DataExploreService service;
  @Autowired private ExploreQueries queries;
  @Autowired private JobPostingRepository jobPostingRepository;
  @Autowired private MarketAggregateService aggregate;

  private void cpi(String month, String code, String value) {
    cpiRepository.saveAndFlush(
        CpiObservation.builder()
            .baseYearMonth(month)
            .category(code)
            .cpiValue(new BigDecimal(value))
            .build());
  }

  @Test
  @DisplayName("CPI 기본 범위의 끝은 UTC 기준 이번 달")
  void cpi_defaultRangeEndsAtUtcMonth() {
    cpi("202610", "A", "111.00");
    cpi("202611", "A", "112.00");
    Clock clock = Clock.fixed(Instant.parse("2026-10-31T16:00:00Z"), ZoneOffset.UTC); // 서울 11월 1일
    DataExploreService fixed =
        new DataExploreService(queries, jobPostingRepository, aggregate, clock);

    CpiTable t = fixed.cpi(null, null);

    assertThat(t.months()).extracting(CpiTable.Month::month).containsExactly("202610");
  }

  @Test
  @DisplayName("CPI 월 × 품목 표와 12개 품목이 다 있지 않은 달")
  void cpi_table() {
    for (String c : CpiTable.CODES) {
      cpi("202608", c, "110.00");
    }
    cpi("202609", "A", "111.00");

    CpiTable t = service.cpi("202608", "202609");

    assertThat(t.codes()).hasSize(12);
    assertThat(t.months()).hasSize(2);
    assertThat(t.missing()).hasSize(1);
    assertThat(t.missing().getFirst().month()).isEqualTo("202609");
    assertThat(t.missing().getFirst().codes()).hasSize(11).doesNotContain("A");
  }

  @Test
  @DisplayName("행이 하나도 없는 달도 첫 달~마지막 달 안이면 빈 값과 전 품목 누락으로 나온다")
  void cpi_zeroRowMonthInsideBounds() {
    for (String c : CpiTable.CODES) {
      cpi("202606", c, "110.00");
      cpi("202608", c, "112.00");
    }

    CpiTable t = service.cpi("202601", "202612");

    assertThat(t.months())
        .extracting(CpiTable.Month::month)
        .containsExactly("202606", "202607", "202608");
    assertThat(t.months().get(0).values().get("A")).isEqualByComparingTo("110.00");
    assertThat(t.months().get(1).values()).isEmpty();
    assertThat(t.missing()).hasSize(1);
    assertThat(t.missing().getFirst().month()).isEqualTo("202607");
    assertThat(t.missing().getFirst().codes()).isEqualTo(CpiTable.CODES);
  }

  @Test
  @DisplayName("엔진 기준선은 core-api 와 같은 규칙 — 전국 우선, 출처(KOSIS 먼저) → 세전 총액 → 최신 연도 → 최신 회차")
  void wage_engineBaseline() {
    wage(
        "DEVELOPER", "MID", "ALL", "ANNUAL_GROSS_MEDIAN", 70_000_000, "2025", "202610", "FALLBACK");
    wage("DEVELOPER", "MID", "ALL", "ANNUAL_GROSS_MEDIAN", 60_000_000, "2024", "202408", "KOSIS");
    wage("DEVELOPER", "MID", "ALL", "ANNUAL_BASE_MEDIAN", 55_000_000, "2025", "202508", "KOSIS");
    wage("DATA", "MID", "ALL", "ANNUAL_GROSS_MEDIAN", 50_000_000, "2025", "202508", "KOSIS");
    wage("DATA", "MID", "전국", "ANNUAL_GROSS_MEDIAN", 49_000_000, "2024", "202408", "KOSIS");
    wage("AI", "LEAD", "ALL", "ANNUAL_GROSS_MEDIAN", 80_000_000, "2024", "202408", "KOSIS");

    WageTable t = service.wage();

    assertThat(t.engineBaselines())
        .extracting(
            WageTable.Baseline::jobFamily,
            WageTable.Baseline::experienceBracket,
            WageTable.Baseline::region,
            WageTable.Baseline::basePeriod,
            WageTable.Baseline::source,
            WageTable.Baseline::wageMedian)
        .containsExactly(
            tuple("DEVELOPER", "MID", "ALL", "2024", "KOSIS", 60_000_000L),
            tuple("DATA", "MID", "전국", "2024", "KOSIS", 49_000_000L),
            tuple("AI", "LEAD", "ALL", "2024", "KOSIS", 80_000_000L));
    assertThat(t.missing()).contains("AI/LEAD");
  }

  private void wage(
      String family,
      String bracket,
      String region,
      String definition,
      long median,
      String basePeriod,
      String vintage,
      String source) {
    jdbc.update(
        "INSERT INTO core.wage_stat_observation (id, job_family, experience_bracket, region,"
            + " wage_definition, wage_median, base_period, stat_vintage, source)"
            + " VALUES (RANDOM_UUID(), ?, ?, ?, ?, ?, ?, ?, ?)",
        family,
        bracket,
        region,
        definition,
        median,
        basePeriod,
        vintage,
        source);
  }
}
