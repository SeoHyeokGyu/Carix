package com.carix.index;

import com.carix.income.domain.IncomeHistory;

/**
 * 소득 이력 행의 실효 소득. 계약 금액 밖에서 따로 받는 고정상여(연액)를 구매력 지수에 반영하기 위한 값이다. 지급월 스파이크를 피하려고 연액/12 로 매달 나눠 본다.
 */
public final class EffectiveIncome {

  private static final int MONTHS_PER_YEAR = 12;

  private EffectiveIncome() {}

  /** 세전 연 소득 = 연봉 + (상여가 따로면 고정상여 연액). */
  public static long annualGross(IncomeHistory h) {
    return h.getGrossSalary() + separateBonus(h);
  }

  /** 월 실수령액. 실수령액이 없으면 {@code null}. 따로 받는 상여는 그 행의 실효 수령률(실수령 × 12 / 연봉)로 환산해 월 실수령에 더한다. */
  public static Long monthlyNet(IncomeHistory h) {
    Long net = h.getNetSalary();
    if (net == null) {
      return null;
    }
    long bonus = separateBonus(h);
    if (bonus == 0 || h.getGrossSalary() <= 0) {
      return net;
    }
    double monthlyBonus = bonus / (double) MONTHS_PER_YEAR;
    double netRate = net * (double) MONTHS_PER_YEAR / h.getGrossSalary();
    return net + Math.round(monthlyBonus * netRate);
  }

  private static long separateBonus(IncomeHistory h) {
    Long bonus = h.getFixedBonusAnnual();
    if (h.getGrossSalary() <= 0 || h.isBonusIncluded() || bonus == null) {
      return 0L;
    }
    return bonus;
  }
}
