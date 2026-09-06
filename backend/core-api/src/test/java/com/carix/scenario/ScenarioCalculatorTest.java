package com.carix.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 잔류·이직 12개월 보상 계산 (도메인 정의서 §4).
 *
 * <p>종전 컨트롤러는 실업급여를 {@code isEligible ? 3960000L : 0L} 로 고정했다. 그 값은 연봉 6천만·공백 2개월일 때의 결과 하나라 공백이
 * 0개월이어도 396만원이 나왔다. 여기서 공식을 못 박는다.
 */
class ScenarioCalculatorTest {

  /** 도메인 정의서 §4.1 의 가정치. 실제 값은 설정에서 온다. */
  private final ScenarioCalculator calculator =
      new ScenarioCalculator(new UnemploymentBenefitAssumption(66_000L, 60_120L, "2026-01"));

  @Test
  @DisplayName("API 규격 §6.1 예시를 그대로 재현한다")
  void 규격_예시() {
    ScenarioOutcome outcome =
        calculator.calculate(60_000_000L, 3.5, 72_000_000L, 3_000_000L, 2, true);

    assertThat(outcome.stayCompensation12m()).isEqualTo(62_100_000L);
    assertThat(outcome.estimatedUnemploymentBenefit()).isEqualTo(3_960_000L);
    assertThat(outcome.moveCompensation12m()).isEqualTo(60_960_000L);
    assertThat(outcome.difference()).isEqualTo(-1_140_000L);
    assertThat(outcome.type()).isEqualTo("ESTIMATED_OPPORTUNITY_COST");
    assertThat(outcome.typeLabel()).isEqualTo("추정 기회비용");
  }

  @Test
  @DisplayName("공백이 0개월이면 실업급여도 0이다")
  void 공백_없음() {
    ScenarioOutcome outcome = calculator.calculate(60_000_000L, 3.5, 72_000_000L, 0L, 0, true);

    assertThat(outcome.estimatedUnemploymentBenefit()).isZero();
    assertThat(outcome.moveCompensation12m()).isEqualTo(72_000_000L);
  }

  @Test
  @DisplayName("공백이 6개월이어도 소정급여일수 5개월에서 끊긴다")
  void 수급_상한월수() {
    long sixMonths =
        calculator
            .calculate(60_000_000L, 3.5, 72_000_000L, 0L, 6, true)
            .estimatedUnemploymentBenefit();

    // 66,000 × 30 × min(6, 5)
    assertThat(sixMonths).isEqualTo(9_900_000L);
  }

  @Test
  @DisplayName("일 지급액이 상한을 넘으면 상한으로 자른다")
  void 일지급액_상한() {
    // 0.6 × 60,000,000 / 365 = 98,630원 → 상한 66,000
    long benefit =
        calculator
            .calculate(60_000_000L, 3.5, 72_000_000L, 0L, 1, true)
            .estimatedUnemploymentBenefit();

    assertThat(benefit).isEqualTo(66_000L * 30);
  }

  @Test
  @DisplayName("일 지급액이 하한에 못 미치면 하한으로 올린다")
  void 일지급액_하한() {
    // 0.6 × 30,000,000 / 365 = 49,315원 → 하한 60,120
    long benefit =
        calculator
            .calculate(30_000_000L, 3.5, 40_000_000L, 0L, 1, true)
            .estimatedUnemploymentBenefit();

    assertThat(benefit).isEqualTo(60_120L * 30);
  }

  @Test
  @DisplayName("상·하한 사이면 평균임금의 60%를 그대로 쓴다")
  void 일지급액_구간내() {
    // 0.6 × 38,000,000 / 365 = 62,465.75 → 62,466
    long benefit =
        calculator
            .calculate(38_000_000L, 3.5, 40_000_000L, 0L, 1, true)
            .estimatedUnemploymentBenefit();

    assertThat(benefit).isEqualTo(62_466L * 30);
  }

  @Test
  @DisplayName("수급 자격이 없으면 공백 개월수와 무관하게 0이다")
  void 수급자격_없음() {
    ScenarioOutcome outcome = calculator.calculate(60_000_000L, 3.5, 72_000_000L, 0L, 3, false);

    assertThat(outcome.estimatedUnemploymentBenefit()).isZero();
    assertThat(outcome.unemploymentBenefitNote()).isEqualTo("실업급여 미반영");
  }

  @Test
  @DisplayName("수급 자격이 있으면 가정 기반 추정임을 기준 시점과 함께 알린다")
  void 가정_기반_추정_라벨() {
    ScenarioOutcome outcome = calculator.calculate(60_000_000L, 3.5, 72_000_000L, 0L, 2, true);

    assertThat(outcome.unemploymentBenefitNote()).isEqualTo("가정 기반 추정 (기준 시점: 2026-01)");
  }

  @Test
  @DisplayName("이직이 더 유리하면 추정 추가보상이다")
  void 추가보상() {
    ScenarioOutcome outcome =
        calculator.calculate(60_000_000L, 3.0, 90_000_000L, 1_000_000L, 1, true);

    assertThat(outcome.difference()).isPositive();
    assertThat(outcome.type()).isEqualTo("ESTIMATED_GAIN");
    assertThat(outcome.typeLabel()).isEqualTo("추정 추가보상");
  }

  @Test
  @DisplayName("차이가 정확히 0이면 추가보상 쪽이다")
  void 차이_0() {
    // 잔류 = 50,000,000 × 1.0 = 50,000,000. 이직 = 50,000,000 × 12/12 + 0 - 0
    ScenarioOutcome outcome = calculator.calculate(50_000_000L, 0.0, 50_000_000L, 0L, 0, true);

    assertThat(outcome.difference()).isZero();
    assertThat(outcome.type()).isEqualTo("ESTIMATED_GAIN");
  }

  @Test
  @DisplayName("인상률의 이진 오차가 원 단위를 흔들지 않는다")
  void 반올림() {
    // 1.035 는 이진 표현이 정확하지 않다. 절삭하면 62,099,999 가 나올 수 있다.
    assertThat(calculator.calculate(60_000_000L, 3.5, 1L, 0L, 0, false).stayCompensation12m())
        .isEqualTo(62_100_000L);
  }
}
