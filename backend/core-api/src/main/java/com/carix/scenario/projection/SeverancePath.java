package com.carix.scenario.projection;

import com.carix.scenario.severance.SeveranceCalculator;
import com.carix.scenario.tax.RetirementIncomeTax;
import java.time.LocalDate;

/**
 * 「m 개월째 말에 나가면 받을 세후 퇴직금」(명목). 평균임금은 투영기의 그 달 연봉 경로를 상수로 쓴다 — 같은 달의 월 소득과 퇴직금이 같은 연봉을 봐야 비교가 맞다.
 */
final class SeverancePath {

  private SeverancePath() {}

  /** 지금 회사에 남아 m 개월째 말에 나갈 때. */
  static long stay(SeveranceTerms terms, int month, long annualGross) {
    return net(terms.hireDate(), terms.today().plusMonths(month), annualGross);
  }

  /** 오늘 지금 회사를 나갈 때. 이직 쪽 모든 달에 같은 값으로 붙는다. */
  static long currentCompany(SeveranceTerms terms, long annualGross) {
    return net(terms.hireDate(), terms.today(), annualGross);
  }

  /** 쉬는 기간 뒤 새 회사에 들어가 m 개월째 말에 나갈 때. 새 회사 근속 1년 미만이면 0. */
  static long newCompany(
      SeveranceTerms terms, int unemploymentMonths, int month, long annualGross) {
    LocalDate newHireDate = terms.today().plusMonths(unemploymentMonths).plusDays(1);
    return net(newHireDate, terms.today().plusMonths(month), annualGross);
  }

  private static long net(LocalDate hireDate, LocalDate lastWorkDay, long annualGross) {
    long gross = SeveranceCalculator.grossSeverance(hireDate, lastWorkDay, date -> annualGross);
    return RetirementIncomeTax.of(gross, hireDate, lastWorkDay).net(gross);
  }
}
