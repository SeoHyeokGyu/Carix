package com.carix.index;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * {@link CurrentSalary}·{@link WageBaseline} 은 금액을 담는 레코드다. 완료 기준 10 이 민감 금액의 로그 유출을 금지하므로, 기본
 * {@code toString()} 이 금액을 그대로 찍지 않는지 확인한다.
 */
class SensitiveAmountToStringTest {

  @Test
  void CurrentSalary_toString은_연봉_금액을_담지_않는다() {
    CurrentSalary salary = new CurrentSalary(72_000_000L, LocalDate.of(2026, 1, 1));

    assertThat(salary.toString()).doesNotContain("72000000").contains("2026-01-01");
  }

  @Test
  void WageBaseline_toString은_중앙값_금액을_담지_않는다() {
    WageBaseline baseline = new WageBaseline(72_000_000L, "초과급여 제외, 상여 포함", "202606");

    assertThat(baseline.toString())
        .doesNotContain("72000000")
        .contains("초과급여 제외, 상여 포함")
        .contains("202606");
  }
}
