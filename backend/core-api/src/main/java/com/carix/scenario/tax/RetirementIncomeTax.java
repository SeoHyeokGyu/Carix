package com.carix.scenario.tax;

import com.carix.scenario.ServicePeriod;
import java.time.LocalDate;

/** 법정 퇴직금에 붙는 퇴직소득세와 지방소득세 (소득세법 제48조·제55조, 2023년 이후 귀속분). */
public final class RetirementIncomeTax {

  private static final int MONTHS = 12;
  private static final double LOCAL_TAX_RATE = 0.1;

  private RetirementIncomeTax() {}

  public record RetirementTax(long incomeTax, long localTax) {

    public long total() {
      return incomeTax + localTax;
    }

    public long net(long gross) {
      return gross - total();
    }
  }

  /** lastWorkDay 는 마지막 근무일이다. 퇴직일은 그 다음 날, 재직기간은 입사일~마지막 근무일 양끝 포함. */
  public static RetirementTax of(long grossSeverance, LocalDate hireDate, LocalDate lastWorkDay) {
    if (grossSeverance <= 0) {
      return new RetirementTax(0L, 0L);
    }
    int years = serviceYears(hireDate, lastWorkDay);
    long converted =
        Math.round((double) (grossSeverance - serviceDeduction(years)) * MONTHS / years);
    long taxBase = Math.max(0L, converted - convertedDeduction(converted));
    long incomeTax = Math.round(TakeHomeEstimator.progressiveTax(taxBase) * years / MONTHS);
    return new RetirementTax(incomeTax, Math.round(incomeTax * LOCAL_TAX_RATE));
  }

  /** 1년 미만 끝수는 1년으로 올린다. N년이 채워지는 날은 퇴직금 1년 요건과 같은 규칙(민법 제160조)으로 센다. */
  static int serviceYears(LocalDate hireDate, LocalDate lastWorkDay) {
    int fullYears = 0;
    while (!ServicePeriod.nthYearLastWorkDay(hireDate, fullYears + 1).isAfter(lastWorkDay)) {
      fullYears++;
    }
    boolean hasRemainder =
        fullYears == 0
            || ServicePeriod.nthYearLastWorkDay(hireDate, fullYears).isBefore(lastWorkDay);
    return Math.max(1, fullYears + (hasRemainder ? 1 : 0));
  }

  static long serviceDeduction(int years) {
    if (years <= 5) {
      return 1_000_000L * years;
    }
    if (years <= 10) {
      return 5_000_000L + 2_000_000L * (years - 5);
    }
    if (years <= 20) {
      return 15_000_000L + 2_500_000L * (years - 10);
    }
    return 40_000_000L + 3_000_000L * (years - 20);
  }

  static long convertedDeduction(long converted) {
    if (converted <= 8_000_000L) {
      return converted;
    }
    if (converted <= 70_000_000L) {
      return 8_000_000L + Math.round((converted - 8_000_000L) * 0.6);
    }
    if (converted <= 100_000_000L) {
      return 45_200_000L + Math.round((converted - 70_000_000L) * 0.55);
    }
    if (converted <= 300_000_000L) {
      return 61_700_000L + Math.round((converted - 100_000_000L) * 0.45);
    }
    return 151_700_000L + Math.round((converted - 300_000_000L) * 0.35);
  }
}
