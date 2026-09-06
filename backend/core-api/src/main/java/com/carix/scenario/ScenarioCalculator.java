package com.carix.scenario;

import org.springframework.stereotype.Component;

/**
 * 잔류 vs 이직 12개월 보상을 계산한다 (도메인 정의서 §4).
 *
 * <p>컨트롤러에서 분리한 이유는 실업급여 때문이다. 종전 구현은 {@code isEligible ? 3960000L : 0L} 로 고정값을 썼는데, 그 값은 연봉 6천만·공백
 * 2개월일 때의 결과 하나다 — 공백이 0개월이어도 396만원이 나왔고 규격 예시와 우연히 맞아 드러나지 않았다.
 *
 * <p>세전 기준이며 세금은 추정하지 않는다.
 */
@Component
public class ScenarioCalculator {

  /** 소정급여일수를 월로 단순화한 상한 (도메인 정의서 §4.1 — 평균 150일). */
  private static final int MAX_BENEFIT_MONTHS = 5;

  private static final int DAYS_PER_YEAR = 365;
  private static final int DAYS_PER_MONTH = 30;
  private static final double BENEFIT_RATE = 0.6;
  private static final int MONTHS_PER_YEAR = 12;

  private static final String GAIN = "ESTIMATED_GAIN";
  private static final String OPPORTUNITY_COST = "ESTIMATED_OPPORTUNITY_COST";

  private final UnemploymentBenefitAssumption assumption;

  public ScenarioCalculator(UnemploymentBenefitAssumption assumption) {
    this.assumption = assumption;
  }

  /**
   * @param currentGrossSalary 시뮬레이션에 실제 쓰는 현재 세전 연봉. 가상 입력이면 그 값이다 — 실업급여 평균임금도 같은 값을 쓴다. 한 응답 안에서
   *     잔류 보상과 실업급여가 서로 다른 연봉을 기준으로 나오면 숫자가 맞지 않는다
   * @param stayExpectedRaiseRate 잔류 시 예상 인상률(%)
   * @param moveTargetGrossSalary 이직 목표 세전 연봉
   * @param moveOneTimeCost 이직 부대비용
   * @param expectedUnemploymentMonths 예상 공백 개월수 (0~6)
   * @param eligibleForBenefit 실업급여 수급 자격 여부
   */
  public ScenarioOutcome calculate(
      long currentGrossSalary,
      double stayExpectedRaiseRate,
      long moveTargetGrossSalary,
      long moveOneTimeCost,
      int expectedUnemploymentMonths,
      boolean eligibleForBenefit) {

    // 인상률은 이진 표현이 정확하지 않다(1.035 등). 절삭하면 원 단위가 1 어긋나므로 반올림한다.
    long stay = Math.round(currentGrossSalary * (1 + stayExpectedRaiseRate / 100));

    long benefit =
        eligibleForBenefit ? totalBenefit(currentGrossSalary, expectedUnemploymentMonths) : 0L;

    long earned =
        Math.round(
            (double) moveTargetGrossSalary
                * (MONTHS_PER_YEAR - expectedUnemploymentMonths)
                / MONTHS_PER_YEAR);
    long move = earned + benefit - moveOneTimeCost;
    long difference = move - stay;

    return new ScenarioOutcome(
        stay,
        move,
        benefit,
        difference,
        typeOf(difference),
        typeLabelOf(difference),
        benefitNote(eligibleForBenefit));
  }

  /** 규격이 고정한 결과 유형. 차이가 정확히 0이면 추가보상 쪽이다. */
  public static String typeOf(long difference) {
    return difference >= 0 ? GAIN : OPPORTUNITY_COST;
  }

  /** 화면 표기. 규격이 이 두 문구로 고정한다 — `추정 기회이익` 등 다른 표기는 쓰지 않는다. */
  public static String typeLabelOf(long difference) {
    return difference >= 0 ? "추정 추가보상" : "추정 기회비용";
  }

  /** {@code min(U, 5) × 일지급액 × 30}. */
  private long totalBenefit(long grossSalary, int unemploymentMonths) {
    int months = Math.min(unemploymentMonths, MAX_BENEFIT_MONTHS);
    return months * dailyBenefit(grossSalary) * DAYS_PER_MONTH;
  }

  /** {@code min(상한, max(하한, 0.6 × 연봉/365))}. */
  private long dailyBenefit(long grossSalary) {
    long sixtyPercent = Math.round(BENEFIT_RATE * grossSalary / DAYS_PER_YEAR);
    return Math.min(
        assumption.dailyUpperLimit(), Math.max(assumption.dailyLowerLimit(), sixtyPercent));
  }

  /** 저장분을 되살릴 때도 쓰므로 공개한다. 기준 시점은 항상 <b>현재</b> 설정값이다. */
  public String benefitNote(boolean eligibleForBenefit) {
    return eligibleForBenefit
        ? "가정 기반 추정 (기준 시점: %s)".formatted(assumption.assumptionAsOf())
        : "실업급여 미반영";
  }
}
