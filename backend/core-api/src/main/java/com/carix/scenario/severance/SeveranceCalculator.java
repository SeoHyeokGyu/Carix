package com.carix.scenario.severance;

import com.carix.scenario.ServicePeriod;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.function.ToLongFunction;

/** 법정 퇴직금(근로자퇴직급여 보장법). 「X 퇴사」는 X 가 마지막 근무일이고, 퇴직일은 X + 1일이다. */
public final class SeveranceCalculator {

  private static final int MONTHS_IN_WINDOW = 3;
  private static final int MONTHS_PER_YEAR = 12;
  private static final int SEVERANCE_DAYS_PER_YEAR = 30;
  private static final int DAYS_PER_YEAR = 365;

  private SeveranceCalculator() {}

  /** 세전 퇴직금(원). annualGrossOn 은 그 날짜에 유효한 상여 포함 세전 연봉. 1년 미만이면 0. */
  public static long grossSeverance(
      LocalDate hireDate, LocalDate lastWorkDay, ToLongFunction<LocalDate> annualGrossOn) {
    if (!isEligible(hireDate, lastWorkDay)) {
      return 0L;
    }
    LocalDate retirementDate = lastWorkDay.plusDays(1);
    LocalDate windowStart = retirementDate.minusMonths(MONTHS_IN_WINDOW);
    long windowDays = ChronoUnit.DAYS.between(windowStart, retirementDate);
    // 연봉 안의 상여가 3/12 가산과 같아지도록 월 단위 세 시점의 연봉/12 를 합산한다
    double wageTotal = 0;
    for (int i = 0; i < MONTHS_IN_WINDOW; i++) {
      wageTotal += (double) annualGrossOn.applyAsLong(windowStart.plusMonths(i)) / MONTHS_PER_YEAR;
    }
    long employedDays = ChronoUnit.DAYS.between(hireDate, lastWorkDay) + 1;
    return Math.round(
        wageTotal / windowDays * SEVERANCE_DAYS_PER_YEAR * employedDays / DAYS_PER_YEAR);
  }

  public static boolean isEligible(LocalDate hireDate, LocalDate lastWorkDay) {
    return !lastWorkDay.isBefore(anniversaryLastWorkDay(hireDate));
  }

  /** 1년 근속이 채워지는 마지막 근무일. */
  public static LocalDate anniversaryLastWorkDay(LocalDate hireDate) {
    return ServicePeriod.nthYearLastWorkDay(hireDate, 1);
  }
}
