package com.carix.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.carix.dashboard.dto.DashboardSummaryResponse.BonusInclusiveIncome;
import com.carix.income.domain.IncomeHistory;
import com.carix.income.dto.PerformanceBonusResponse;
import com.carix.income.repository.IncomeHistoryRepository;
import com.carix.income.service.PerformanceBonusService;
import com.carix.index.inflation.InflationComparison;
import com.carix.index.inflation.PersonalInflationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BonusInclusiveIncomeCalculatorTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final String BASE = "202610";

  private IncomeHistoryRepository incomeRepository;
  private PerformanceBonusService bonusService;
  private PersonalInflationService inflationService;
  private BonusInclusiveIncomeCalculator calculator;

  @BeforeEach
  void setUp() {
    incomeRepository = mock(IncomeHistoryRepository.class);
    bonusService = mock(PerformanceBonusService.class);
    inflationService = mock(PersonalInflationService.class);
    calculator =
        new BonusInclusiveIncomeCalculator(incomeRepository, bonusService, inflationService);
  }

  @Test
  @DisplayName("기록_없으면_null")
  void 기록_없으면_null() {
    given(bonusService.list(USER_ID)).willReturn(List.of());

    assertThat(calculator.calculate(USER_ID, BASE)).isNull();
  }

  @Test
  @DisplayName("직전_기간_소득이_없으면_실질변화는_null")
  void 직전_기간_소득이_없으면_실질변화는_null() {
    // 최근 12개월(202511~202610) 안에서만 시작한 소득 행
    given(incomeRepository.findByUserIdOrderByEffectiveDateDesc(USER_ID))
        .willReturn(List.of(income(60_000_000L, LocalDate.of(2025, 11, 1))));
    given(bonusService.list(USER_ID)).willReturn(List.of(bonus("202603", 4_000_000L)));
    given(inflationService.trailing12m(USER_ID, BASE)).willReturn(Optional.of(comparison("2.00")));

    BonusInclusiveIncome result = calculator.calculate(USER_ID, BASE);

    assertThat(result.recentAnnual()).isEqualTo(64_000_000L);
    assertThat(result.previousAnnual()).isZero();
    assertThat(result.realChangePercent()).isNull();
    assertThat(result.bonusCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("개인물가가_없으면_실질변화는_null")
  void 개인물가가_없으면_실질변화는_null() {
    given(incomeRepository.findByUserIdOrderByEffectiveDateDesc(USER_ID))
        .willReturn(List.of(income(48_000_000L, LocalDate.of(2020, 1, 1))));
    given(bonusService.list(USER_ID)).willReturn(List.of(bonus("202603", 1_000_000L)));
    given(inflationService.trailing12m(USER_ID, BASE)).willReturn(Optional.empty());

    BonusInclusiveIncome result = calculator.calculate(USER_ID, BASE);

    assertThat(result.realChangePercent()).isNull();
    assertThat(result.recentAnnual()).isEqualTo(49_000_000L);
  }

  @Test
  @DisplayName("성과급과_월할_연봉을_합산한다")
  void 성과급과_월할_연봉을_합산한다() {
    // 4800만 원 -> 2025-11-01 부터 6000만 원. 최근 = 202511~202610, 직전 = 202411~202510
    given(incomeRepository.findByUserIdOrderByEffectiveDateDesc(USER_ID))
        .willReturn(
            List.of(
                income(60_000_000L, LocalDate.of(2025, 11, 1)),
                income(48_000_000L, LocalDate.of(2020, 1, 1))));
    given(bonusService.list(USER_ID))
        .willReturn(
            List.of(
                bonus("202603", 4_000_000L),
                bonus("202501", 2_000_000L),
                bonus("202410", 9_000_000L)));
    given(inflationService.trailing12m(USER_ID, BASE)).willReturn(Optional.of(comparison("2.00")));

    BonusInclusiveIncome result = calculator.calculate(USER_ID, BASE);

    assertThat(result.recentAnnual()).isEqualTo(64_000_000L);
    assertThat(result.previousAnnual()).isEqualTo(50_000_000L);
    // (64/50) / 1.02 - 1 = 25.49%
    assertThat(result.realChangePercent()).isEqualTo(25.5);
    assertThat(result.bonusCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("고정상여는_월할로_연봉에_더해진다")
  void 고정상여는_월할로_연봉에_더해진다() {
    IncomeHistory row = income(48_000_000L, LocalDate.of(2020, 1, 1));
    row.applyBonusTerms(false, 12_000_000L);
    given(incomeRepository.findByUserIdOrderByEffectiveDateDesc(USER_ID)).willReturn(List.of(row));
    given(bonusService.list(USER_ID)).willReturn(List.of(bonus("202603", 1_000_000L)));
    given(inflationService.trailing12m(USER_ID, BASE)).willReturn(Optional.empty());

    BonusInclusiveIncome result = calculator.calculate(USER_ID, BASE);

    assertThat(result.recentAnnual()).isEqualTo(61_000_000L);
    assertThat(result.previousAnnual()).isEqualTo(60_000_000L);
  }

  private static IncomeHistory income(long gross, LocalDate effectiveDate) {
    return new IncomeHistory(USER_ID, gross, null, effectiveDate, null);
  }

  private static PerformanceBonusResponse bonus(String paidMonth, long amount) {
    return new PerformanceBonusResponse(UUID.randomUUID(), paidMonth, amount);
  }

  private static InflationComparison comparison(String personalRate) {
    return new InflationComparison(
        "202609",
        new BigDecimal(personalRate),
        new BigDecimal("2.00"),
        BigDecimal.ZERO,
        null,
        BigDecimal.ZERO);
  }
}
