package com.carix.index;

import java.time.LocalDate;

/**
 * 기준월 시점에 유효한 세전 연봉 (Member A → Member C).
 *
 * @param grossSalary 세전 연봉. 단위는 원이다
 * @param effectiveDate 이 연봉이 적용되기 시작한 날. 기준월 말일 이하 중 가장 늦은 {@code income_history.effective_date} 다
 */
public record CurrentSalary(long grossSalary, LocalDate effectiveDate) {

  /** 금액을 가린다 — 완료 기준 10 이 금액이 로그에 남는 것을 금지한다. 기본 {@code toString()} 은 이를 어긴다. */
  @Override
  public String toString() {
    return "CurrentSalary[grossSalary=***, effectiveDate=%s]".formatted(effectiveDate);
  }
}
