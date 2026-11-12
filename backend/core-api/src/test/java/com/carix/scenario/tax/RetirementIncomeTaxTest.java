package com.carix.scenario.tax;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RetirementIncomeTaxTest {

  @Test
  void 근속_3년_1500만() {
    var t = RetirementIncomeTax.of(15_000_000L, LocalDate.of(2023, 3, 2), LocalDate.of(2026, 3, 1));

    // 근속연수공제 3×100만 = 300만 → 환산 (1,500−300)×12/3 = 4,800만
    // 환산급여공제 800만+(4,800−800)만×60% = 3,200만 → 과세표준 1,600만
    // 기본세율 1,600만×15%−126만 = 114만 → ×3/12 = 28.5만
    assertThat(t.incomeTax()).isEqualTo(285_000L);
    assertThat(t.localTax()).isEqualTo(28_500L);
    assertThat(t.net(15_000_000L)).isEqualTo(15_000_000L - 285_000L - 28_500L);
  }

  @Test
  void 근속_12년_8000만() {
    var t = RetirementIncomeTax.of(80_000_000L, LocalDate.of(2014, 3, 2), LocalDate.of(2026, 3, 1));

    // 홈택스 모의계산 대조는 수동 확인 필요. 아래는 spec 4.2 공식 손계산.
    // 근속연수공제 1,500만+250만×(12−10) = 2,000만 → 환산 (8,000−2,000)×12/12 = 6,000만
    // 환산급여공제 800만+(6,000−800)만×60% = 3,920만 → 과세표준 2,080만
    // 기본세율 2,080만×15%−126만 = 186만 → ×12/12 = 186만, 지방소득세 18.6만
    assertThat(t.incomeTax()).isEqualTo(1_860_000L);
    assertThat(t.localTax()).isEqualTo(186_000L);
  }

  @Test
  void 퇴직금_0원이면_세금도_0원() {
    var t = RetirementIncomeTax.of(0L, LocalDate.of(2023, 3, 2), LocalDate.of(2026, 3, 1));

    assertThat(t.total()).isZero();
  }

  @Test
  void 근속연수는_하루라도_넘으면_올림() {
    assertThat(RetirementIncomeTax.serviceYears(LocalDate.of(2023, 3, 2), LocalDate.of(2026, 3, 1)))
        .isEqualTo(3);
    assertThat(RetirementIncomeTax.serviceYears(LocalDate.of(2023, 3, 2), LocalDate.of(2026, 3, 2)))
        .isEqualTo(4);
  }

  @ParameterizedTest
  @CsvSource({"5,5000000", "6,7000000", "10,15000000", "11,17500000", "20,40000000", "21,43000000"})
  void 근속연수공제_구간_경계(int years, long expected) {
    assertThat(RetirementIncomeTax.serviceDeduction(years)).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({"8000000,8000000", "70000000,45200000", "100000000,61700000", "300000000,151700000"})
  void 환산급여공제_구간_경계(long converted, long expected) {
    assertThat(RetirementIncomeTax.convertedDeduction(converted)).isEqualTo(expected);
  }
}
