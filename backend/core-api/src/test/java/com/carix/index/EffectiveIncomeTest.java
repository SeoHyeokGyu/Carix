package com.carix.index;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.income.domain.IncomeHistory;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EffectiveIncomeTest {

  private static IncomeHistory income(long gross, Long net, boolean included, Long bonus) {
    IncomeHistory h =
        new IncomeHistory(UUID.randomUUID(), gross, net, LocalDate.of(2026, 1, 1), null);
    h.applyBonusTerms(included, bonus);
    return h;
  }

  @Test
  @DisplayName("상여가 들어 있으면 연봉 그대로")
  void 들어있음이면_연봉_그대로() {
    IncomeHistory h = income(60_000_000L, 4_000_000L, true, null);

    assertThat(EffectiveIncome.annualGross(h)).isEqualTo(60_000_000L);
    assertThat(EffectiveIncome.monthlyNet(h)).isEqualTo(4_000_000L);
  }

  @Test
  @DisplayName("따로 받으면 연액을 더한다")
  void 따로받음이면_연액을_더한다() {
    IncomeHistory h = income(60_000_000L, null, false, 6_000_000L);

    assertThat(EffectiveIncome.annualGross(h)).isEqualTo(66_000_000L);
  }

  @Test
  @DisplayName("따로 받는데 연액을 모르면 연봉 그대로")
  void 따로받음인데_연액_없으면_연봉_그대로() {
    IncomeHistory h = income(60_000_000L, 4_000_000L, false, null);

    assertThat(EffectiveIncome.annualGross(h)).isEqualTo(60_000_000L);
    assertThat(EffectiveIncome.monthlyNet(h)).isEqualTo(4_000_000L);
  }

  @Test
  @DisplayName("실수령 경로는 행의 실효 수령률로 환산")
  void 실수령_경로는_행의_실효_수령률로_환산() {
    // 실효 수령률 4_000_000 × 12 / 60_000_000 = 0.8, 상여 월 500_000 × 0.8 = 400_000
    IncomeHistory h = income(60_000_000L, 4_000_000L, false, 6_000_000L);

    assertThat(EffectiveIncome.monthlyNet(h)).isEqualTo(4_400_000L);
  }

  @Test
  @DisplayName("실수령이 없으면 null")
  void 실수령이_없으면_null() {
    assertThat(EffectiveIncome.monthlyNet(income(60_000_000L, null, false, 6_000_000L))).isNull();
  }
}
