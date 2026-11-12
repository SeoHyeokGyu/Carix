package com.carix.scenario.severance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SeveranceCalculatorTest {

  @Test
  void 근속_3년_연봉_6천이면_법정_공식대로() {
    long g =
        SeveranceCalculator.grossSeverance(
            LocalDate.of(2023, 3, 2), LocalDate.of(2026, 3, 1), d -> 60_000_000L);
    assertThat(g).isEqualTo(15_013_699L); // 1,500만/90일×30×1096/365
  }

  @Test
  void 마지막_근무일이_1주년_이틀_전이면_0_전날이면_지급() {
    var hire = LocalDate.of(2025, 3, 2);
    assertThat(
            SeveranceCalculator.grossSeverance(hire, LocalDate.of(2026, 2, 28), d -> 60_000_000L))
        .isZero();
    assertThat(SeveranceCalculator.grossSeverance(hire, LocalDate.of(2026, 3, 1), d -> 60_000_000L))
        .isPositive();
  }

  @Test
  void 윤일_입사는_다음해_2월_28일까지_근무하면_지급() {
    // 민법 제160조: plusYears(1).minusDays(1) 이면 2/27 로 하루 앞당겨진다
    assertThat(SeveranceCalculator.isEligible(LocalDate.of(2024, 2, 29), LocalDate.of(2025, 2, 27)))
        .isFalse();
    assertThat(SeveranceCalculator.isEligible(LocalDate.of(2024, 2, 29), LocalDate.of(2025, 2, 28)))
        .isTrue();
  }

  // 2026-11-13: 1년 끝을 입사 전날+1년으로 구해, 3/1 입사가 윤년 2/29 를 건너 2/28 에 1년을 채운 것으로 봤다.
  // 민법 제160조: 1년 뒤 입사 해당일의 전날(2/29)까지 일해야 1년이다.
  @Test
  void 삼월_1일_입사는_윤년이면_2월_29일까지_근무해야_지급() {
    assertThat(SeveranceCalculator.isEligible(LocalDate.of(2023, 3, 1), LocalDate.of(2024, 2, 28)))
        .isFalse();
    assertThat(SeveranceCalculator.isEligible(LocalDate.of(2023, 3, 1), LocalDate.of(2024, 2, 29)))
        .isTrue();
  }

  @Test
  void 마지막_3개월에_인상이_들면_평균임금이_오른다() {
    var hire = LocalDate.of(2023, 3, 2);
    var raise = LocalDate.of(2026, 2, 1);
    long flat =
        SeveranceCalculator.grossSeverance(hire, LocalDate.of(2026, 3, 31), d -> 60_000_000L);
    long raised =
        SeveranceCalculator.grossSeverance(
            hire, LocalDate.of(2026, 3, 31), d -> d.isBefore(raise) ? 60_000_000L : 66_000_000L);
    assertThat(raised).isGreaterThan(flat);
  }
}
