package com.carix.wagestat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WageStatInterpolatorTest {

  private final WageStatInterpolator interpolator = new WageStatInterpolator();

  @Test
  @DisplayName("calculateAnnualMedianWage - 균등 분포에서 정확한 중앙값 연봉을 보간 산출한다")
  void calculateAnnualMedianWage_interpolatesCorrectly() {
    // Given: A19(3000~3500)에 100명, A20(3500~4000)에 100명 (총 200명)
    // 50% 지점 = 100번째 사람 -> A19의 상한 = 3500천원
    Map<String, Long> counts =
        Map.of(
            "A19", 100L,
            "A20", 100L);

    // When
    long medianWage = interpolator.calculateAnnualMedianWage(counts);

    // Then: 월 3,500천원 * 12 = 42,000,000원
    assertThat(medianWage).isEqualTo(42_000_000L);
  }

  @Test
  @DisplayName("calculateAnnualPercentileWage - 25% 및 75% 분위수 연봉을 산출한다")
  void calculateAnnualPercentileWage_calculatesP25AndP75() {
    // Given: A18(2800~3000) 100명, A19(3000~3500) 100명, A20(3500~4000) 100명, A21(4000~4500) 100명 (총
    // 400명)
    // P25 (100번째) -> A18의 상한 = 3000천원 (연봉 3,600만원)
    // P75 (300번째) -> A20의 상한 = 4000천원 (연봉 4,800만원)
    Map<String, Long> counts =
        Map.of(
            "A18", 100L,
            "A19", 100L,
            "A20", 100L,
            "A21", 100L);

    // When
    long p25 = interpolator.calculateAnnualPercentileWage(counts, 0.25);
    long p75 = interpolator.calculateAnnualPercentileWage(counts, 0.75);

    // Then
    assertThat(p25).isEqualTo(36_000_000L);
    assertThat(p75).isEqualTo(48_000_000L);
  }
}
