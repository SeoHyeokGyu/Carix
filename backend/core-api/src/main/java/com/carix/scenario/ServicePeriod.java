package com.carix.scenario;

import java.time.LocalDate;

/** 근속 기간 계산. 퇴직금 1년 요건과 퇴직소득세 근속연수가 같은 규칙을 쓴다. */
public final class ServicePeriod {

  private ServicePeriod() {}

  /**
   * N년 근속이 채워지는 마지막 근무일 (민법 제160조). N년 뒤 입사 해당일의 전날이고, 해당일이 없으면(2/29 입사 + 평년) 그달 말일이다.
   *
   * <p>입사 전날 + N년으로 구하면 3/1 입사가 윤년 2/29 를 건너 2/28 에 끝나 하루 일찍 N년을 채운다(2026-11-13 수정).
   */
  public static LocalDate nthYearLastWorkDay(LocalDate hireDate, int years) {
    LocalDate anniversary = hireDate.plusYears(years);
    boolean noCorrespondingDay = anniversary.getDayOfMonth() != hireDate.getDayOfMonth();
    return noCorrespondingDay ? anniversary : anniversary.minusDays(1);
  }
}
