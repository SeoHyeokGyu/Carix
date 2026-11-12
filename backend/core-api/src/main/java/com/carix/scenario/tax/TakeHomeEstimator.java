package com.carix.scenario.tax;

import org.springframework.stereotype.Component;

/** 세전 연봉에서 세후 연봉을 대략 추정한다. 본인 1인 공제만 보는 근사치이며 사용자 입력 실수령액으로 보정한다. */
@Component
public class TakeHomeEstimator {

  public static final String NET_NOT_ENTERED = "NET_NOT_ENTERED";
  public static final String NET_CALIBRATION_SKIPPED = "NET_CALIBRATION_SKIPPED";

  private static final double MIN_RATIO = 0.9;
  private static final double MAX_RATIO = 1.1;
  private static final int MONTHS = 12;

  private static final long PERSONAL_DEDUCTION = 1_500_000L;
  private static final long STANDARD_TAX_CREDIT = 130_000L;
  private static final double LOCAL_TAX_RATE = 0.1;
  private static final long EARNED_DEDUCTION_CAP = 20_000_000L;

  private static final long CREDIT_BASE_LIMIT = 1_300_000L;
  private static final long CREDIT_BASE_AMOUNT = 715_000L;
  private static final double CREDIT_LOW_RATE = 0.55;
  private static final double CREDIT_HIGH_RATE = 0.3;

  // 과세표준 상한, 세율, 누진공제
  private static final long[][] BRACKET_LIMIT_AND_DEDUCTION = {
    {14_000_000L, 0L},
    {50_000_000L, 1_260_000L},
    {88_000_000L, 5_760_000L},
    {150_000_000L, 15_440_000L},
    {300_000_000L, 19_940_000L},
    {500_000_000L, 25_940_000L},
    {1_000_000_000L, 35_940_000L},
    {Long.MAX_VALUE, 65_940_000L}
  };
  private static final double[] BRACKET_RATE = {0.06, 0.15, 0.24, 0.35, 0.38, 0.40, 0.42, 0.45};

  private final TaxAssumption assumption;

  public TakeHomeEstimator(TaxAssumption assumption) {
    this.assumption = assumption;
  }

  /** 근로자 부담 4대보험·소득세·지방소득세를 뺀 연 세후(원). */
  public long annualNet(long annualGross) {
    double gross = annualGross;
    double pension = pensionAnnual(annualGross);
    double health = annualGross * assumption.healthRate();
    double longTermCare = health * assumption.longTermCareRatio();
    double employment = gross * assumption.employmentRate();
    double insurance = pension + health + longTermCare + employment;

    double taxBase =
        gross
            - earnedIncomeDeduction(annualGross)
            - PERSONAL_DEDUCTION
            - pension
            - health
            - longTermCare
            - employment;
    double computedTax = progressiveTax(Math.max(taxBase, 0));
    double determinedTax =
        Math.max(computedTax - earnedIncomeTaxCredit(computedTax, gross) - STANDARD_TAX_CREDIT, 0);
    double incomeTax = determinedTax * (1 + LOCAL_TAX_RATE);

    return Math.round(gross - insurance - incomeTax);
  }

  /** 입력한 월 실수령액으로 추정 세후를 보정하는 비율. 범위를 벗어나면 입력 오류로 보고 적용하지 않는다. */
  public Calibration calibrate(long annualGross, Long monthlyNetEntered) {
    if (monthlyNetEntered == null) {
      return new Calibration(1.0, false, NET_NOT_ENTERED);
    }
    long estimated = annualNet(annualGross);
    if (estimated <= 0) {
      return new Calibration(1.0, false, NET_CALIBRATION_SKIPPED);
    }
    double ratio = (double) monthlyNetEntered * MONTHS / estimated;
    if (ratio < MIN_RATIO || ratio > MAX_RATIO) {
      return new Calibration(1.0, false, NET_CALIBRATION_SKIPPED);
    }
    return new Calibration(ratio, true, null);
  }

  public String ratesAsOf() {
    return assumption.ratesAsOf();
  }

  private double pensionAnnual(long annualGross) {
    long monthly = annualGross / MONTHS;
    long base =
        Math.min(Math.max(monthly, assumption.pensionIncomeFloor()), assumption.pensionIncomeCap());
    return base * assumption.nationalPensionRate() * MONTHS;
  }

  private static double earnedIncomeDeduction(long gross) {
    double deduction;
    if (gross <= 5_000_000L) {
      deduction = gross * 0.7;
    } else if (gross <= 15_000_000L) {
      deduction = 3_500_000L + (gross - 5_000_000L) * 0.4;
    } else if (gross <= 45_000_000L) {
      deduction = 7_500_000L + (gross - 15_000_000L) * 0.15;
    } else if (gross <= 100_000_000L) {
      deduction = 12_000_000L + (gross - 45_000_000L) * 0.05;
    } else {
      deduction = 14_750_000L + (gross - 100_000_000L) * 0.02;
    }
    return Math.min(deduction, EARNED_DEDUCTION_CAP);
  }

  static double progressiveTax(double taxBase) {
    for (int i = 0; i < BRACKET_RATE.length; i++) {
      if (taxBase <= BRACKET_LIMIT_AND_DEDUCTION[i][0]) {
        return taxBase * BRACKET_RATE[i] - BRACKET_LIMIT_AND_DEDUCTION[i][1];
      }
    }
    throw new IllegalStateException("unreachable: last bracket is unbounded");
  }

  private static double earnedIncomeTaxCredit(double computedTax, double gross) {
    double credit =
        computedTax <= CREDIT_BASE_LIMIT
            ? computedTax * CREDIT_LOW_RATE
            : CREDIT_BASE_AMOUNT + (computedTax - CREDIT_BASE_LIMIT) * CREDIT_HIGH_RATE;
    return Math.min(credit, creditLimit(gross));
  }

  private static double creditLimit(double gross) {
    if (gross <= 33_000_000L) {
      return 740_000;
    }
    if (gross <= 70_000_000L) {
      return Math.max(660_000, 740_000 - (gross - 33_000_000L) * 0.008);
    }
    if (gross <= 120_000_000L) {
      return Math.max(500_000, 660_000 - (gross - 70_000_000L) * 0.5);
    }
    return Math.max(200_000, 500_000 - (gross - 120_000_000L) * 0.5);
  }

  /**
   * @param ratio 보정 비율. 적용하지 않으면 1.0
   * @param applied 보정 적용 여부
   * @param reasonCode 적용하지 않은 사유. 적용했으면 null
   */
  public record Calibration(double ratio, boolean applied, String reasonCode) {}
}
