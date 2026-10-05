package com.carix.pipeline.explore;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.cpi.CpiObservation;
import com.carix.cpi.CpiObservationRepository;
import com.carix.pipeline.dto.explore.CpiTable;
import com.carix.pipeline.dto.explore.WageTable;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
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

  private void cpi(String month, String code, String value) {
    cpiRepository.saveAndFlush(
        CpiObservation.builder()
            .baseYearMonth(month)
            .category(code)
            .cpiValue(new BigDecimal(value))
            .build());
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
  @DisplayName("CPI 가 없으면 빈 표")
  void cpi_empty() {
    CpiTable t = service.cpi("201001", "201002");
    assertThat(t.months()).isEmpty();
  }

  @Test
  @DisplayName("임금통계는 최신 기준 연도의 칸별 출처와 빈 칸만 — 금액 없음")
  void wage_table() throws Exception {
    jdbc.update(
        "INSERT INTO core.wage_stat_observation (id, job_family, experience_bracket, region, wage_definition, wage_median, base_period, stat_vintage, source)"
            + " VALUES (RANDOM_UUID(), 'DEVELOPER', 'MID', 'ALL', 'ANNUAL_GROSS_MEDIAN', 61234567, '2025', '202508', 'KOSIS')");

    WageTable t = service.wage();

    assertThat(t.basePeriod()).isEqualTo("2025");
    assertThat(t.cells()).extracting(WageTable.Cell::jobFamily).containsExactly("DEVELOPER");
    assertThat(t.missing()).hasSize(14).contains("DATA/LEAD");
    assertThat(new ObjectMapper().writeValueAsString(t)).doesNotContain("61234567");
  }

  @Test
  @DisplayName("임금통계가 없으면 기준 연도 null 과 전 칸 빈 칸")
  void wage_empty() {
    WageTable t = service.wage();
    assertThat(t.basePeriod()).isNull();
    assertThat(t.cells()).isEmpty();
    assertThat(t.missing()).hasSize(15);
  }
}
